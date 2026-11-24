"""1 万条语料压测：造数 → 灌 ES → 打点 → 清理。

用法：
  python seed_bench.py seed       # 插入 10000 帖并灌 ES
  python seed_bench.py bench      # 跑查询基准
  python seed_bench.py clean      # 删除压测数据
"""
import json
import random
import sys
import time
import urllib.parse
import urllib.request

import pymysql

DB = dict(host="127.0.0.1", port=3306, user="root", password="123456", database="roam_bupt", charset="utf8mb4")
ES = "http://192.168.153.129:9200/roam_post_v1"
API = "http://127.0.0.1:8080/api/v1"
TOTAL = 10000
PLACE_TOPIC = 79  # 教三楼
PREFIX = "压测-"
RANGES = (100000, 100000 + TOTAL)

WORDS = ["教三", "图书馆", "食堂", "自习", "插座", "安静", "排队", "下雨", "快递", "宿舍"]


def mysql():
    return pymysql.connect(**DB, autocommit=True)


def seed():
    conn = mysql()
    cur = conn.cursor()
    cur.execute("SELECT COALESCE(MAX(id),0) FROM post")
    base = max(cur.fetchone()[0], RANGES[1]) + 1
    rows, topics = [], []
    rnd = random.Random(42)
    now = time.strftime("%Y-%m-%d %H:%M:%S")
    for i in range(TOTAL):
        pid = base + i
        w1, w2 = rnd.sample(WORDS, 2)
        title = f"{PREFIX}{i} {w1}和{w2}的第{i}个角落"
        content = f"这是第 {i} 条压测正文，关键词包括{w1}、{w2}，用来验证大语料下的召回与分页。"
        rows.append((pid, 78, title, content, "ONLINE", 0, now, now))
        topics.append((pid, PLACE_TOPIC))
    cur.executemany("INSERT INTO post(id,user_id,title,content,status,heat,created_time,updated_time) VALUES(%s,%s,%s,%s,%s,%s,%s,%s)", rows)
    cur.executemany("INSERT INTO post_topic(post_id,topic_id) VALUES(%s,%s)", topics)
    print("mysql inserted", TOTAL, "ids", base, "..", base + TOTAL - 1)
    index_es(cur, base)
    conn.close()


def index_es(cur, base):
    # 合成向量：只为压测召回窗口与延迟，不评估语义质量
    rnd = random.Random(7)
    buf = []
    ok = 0
    for i in range(TOTAL):
        pid = base + i
        w1, w2 = rnd.sample(WORDS, 2)
        doc = {
            "postId": pid,
            "title": f"{PREFIX}{i} {w1}和{w2}的第{i}个角落",
            "content": f"这是第 {i} 条压测正文，关键词包括{w1}、{w2}。",
            "authorId": 78,
            "topicIds": [PLACE_TOPIC],
            "status": "ONLINE",
            "createdTime": "2026-11-23T23:00:00",
            "embedding": [rnd.uniform(-1, 1) for _ in range(1024)],
        }
        buf.append(json.dumps({"index": {"_index": "roam_post_v1", "_id": pid}}))
        buf.append(json.dumps(doc))
        if len(buf) >= 2000:
            ok += bulk(buf)
            buf = []
    if buf:
        ok += bulk(buf)
    print("es indexed", ok)


def bulk(lines):
    body = "\n".join(lines) + "\n"
    req = urllib.request.Request(ES + "/_bulk", data=body.encode(), method="POST")
    req.add_header("Content-Type", "application/x-ndjson")
    with urllib.request.urlopen(req, timeout=120) as r:
        data = json.loads(r.read())
    return sum(1 for item in data.get("items", []) if item.get("index", {}).get("status") == 201)


def bench():
    queries = ["教三", "图书馆 自习", "安静看书的地方"]
    for q in queries:
        for page in (1, 2, 11, 41, 61):
            url = f"{API}/search?q={urllib.parse.quote(q)}&page={page}&pageSize=10"
            start = time.time()
            with urllib.request.urlopen(url, timeout=30) as r:
                data = json.loads(r.read())["data"]
            ms = (time.time() - start) * 1000
            print(f"q={q!r} page={page:<3} total={data['total']:<6} returned={len(data['list']):<3} {ms:.0f}ms")
    # ES 侧直查延迟
    body = {"query": {"multi_match": {"query": "教三", "fields": ["title^2", "content"]}}, "size": 500}
    start = time.time()
    req = urllib.request.Request(ES + "/_search", data=json.dumps(body).encode(), method="POST")
    req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=30) as r:
        data = json.loads(r.read())
    print("es bm25 500-hits:", data["hits"]["total"]["value"], f"{(time.time()-start)*1000:.0f}ms")


def clean():
    conn = mysql()
    cur = conn.cursor()
    cur.execute("SELECT id FROM post WHERE title LIKE %s", (PREFIX + "%",))
    ids = [row[0] for row in cur.fetchall()]
    if ids:
        for table in ("post_media", "post_topic", "comment", "notification"):
            cur.execute(f"DELETE FROM {table} WHERE post_id IN (%s)" % ",".join(["%s"] * len(ids)), ids)
        cur.execute("DELETE FROM post WHERE id IN (%s)" % ",".join(["%s"] * len(ids)), ids)
    print("mysql deleted", len(ids))
    if ids:
        body = json.dumps({"query": {"terms": {"postId": ids}}}).encode()
        req = urllib.request.Request(ES + "/_delete_by_query?conflicts=proceed", data=body, method="POST")
        req.add_header("Content-Type", "application/json")
        with urllib.request.urlopen(req, timeout=180) as r:
            print("es deleted", json.loads(r.read()).get("deleted"))
    conn.close()


if __name__ == "__main__":
    {"seed": seed, "bench": bench, "clean": clean}[sys.argv[1]]()
