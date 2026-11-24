"""给本地库灌一批演示数据（帖子/评论/评分/关注），走真实接口。

用法：python seed_demo.py [count]
默认 40 条帖子。重复执行会追加（标题带【示例】前缀，方便清理）。
"""
import json
import os
import random
import sys
import time
import urllib.error
import urllib.request

API = os.environ.get("ROAM_API_BASE", "http://127.0.0.1:8080/api/v1")
IMG_DIR = os.environ.get("ROAM_SEED_IMG_DIR", "")
USERS = ["seed_chen", "seed_zhou", "seed_tang", "seed_lin"]

def load_topics():
    """从接口取真实 id，避免硬编码 id 对不上导致挂空主题。"""
    with urllib.request.urlopen(API + "/topics?type=PLACE&pageSize=200", timeout=20) as r:
        places = [(t["id"], t["name"]) for t in json.loads(r.read())["data"]["list"]]
    with urllib.request.urlopen(API + "/topics?type=CATEGORY&pageSize=100", timeout=20) as r:
        cats = [(t["name"], t["id"]) for t in json.loads(r.read())["data"]["list"]]
    return places, cats

TITLES = [
    "教三四楼靠窗的插座，下午三点后基本靠抢",
    "图书馆四楼几点去还有座？实测一周",
    "下雨天从宿舍到教学楼，这条连廊真的能少淋一半",
    "食堂三楼新窗口测评：排队时间和分量",
    "晚上十点后还有哪间教室开着灯",
    "打印店价格对比：黑白/彩印/装订",
    "快递柜满了之后，临时取件点在哪儿",
    "体育馆羽毛球场预约的坑",
    "校训石旁边那条小路，晚上真的没灯",
    "科学会堂讲座信息一般在哪里看",
    "超市晚上打折的时间点",
    "水房热水最稳定的是哪几个时段",
    "主教学楼一层自习室的开放时间",
    "教二楼和教四楼的连廊能不能走通",
    "瑞幸早上几点不用排队",
    "宿舍楼下外卖被拿错过一次，后来怎么办的",
    "体育场跑步最佳时间段",
    "图书馆借书机坏了去哪还书",
    "食堂哪个窗口加饭不要钱",
    "学校哪里能看到很开阔的日落",
]
BODIES = [
    "实测了几天，把时间点和位置都记下来了，供后来的人参考。",
    "先说结论，再说细节：能早去就早去，晚高峰真的很难找位置。",
    "这条路线亲测有效，雨天尤其省事，路上有两段是干爽的。",
    "价格和等待时间都记了，不同时段差别挺大的。",
    "问了一圈同学，加上自己去看了两次，信息应该比较准。",
    "晚上人少，但要留意闭楼时间，别被锁在里面。",
]
COMMENTS = [
    "有用，收藏了。", "补充一句：周末人更多。", "我上周去过，情况差不多。",
    "请问具体在几楼？", "这周去验证一下。", "太实用了，感谢楼主。",
]
REVIEWS = [
    "环境不错，适合待一下午。", "位置好找，就是高峰期人多。",
    "设施比想象中好。", "一般般，插座不太够。", "安静，推荐。",
]


def call(path, payload=None, token=None, method=None, raw=False):
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(API + path, data=data, method=method or ("POST" if payload is not None else "GET"))
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            body = r.read()
            return r.status, (body if raw else json.loads(body))
    except urllib.error.HTTPError as e:
        return e.code, None


def login():
    tokens = {}
    for name in USERS:
        st, body = call("/auth/login", {"username": name, "password": "secret123"})
        if st == 200:
            tokens[name] = body["data"]["token"]
    return tokens


def upload_image(token, name):
    path = os.path.join(IMG_DIR, name)
    if not os.path.exists(path):
        return None
    boundary = "----roamseed"
    with open(path, "rb") as f:
        content = f.read()
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\n"
            f"Content-Type: image/png\r\n\r\n").encode() + content + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(API + "/files/images", data=body, method="POST")
    req.add_header("Authorization", "Bearer " + token)
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read())["data"]["url"]
    except Exception:
        return None


def main(count=40):
    tokens = login()
    if not tokens:
        print("登录失败，确认本地后端在 8080")
        return
    names = list(tokens)
    rnd = random.Random(20261124)
    images = []
    if IMG_DIR:
        images = [u for u in (upload_image(tokens[names[0]], f"seed{i}.png") for i in range(1, 9)) if u]
    print("images uploaded:", len(images))

    PLACES, CATEGORIES = load_topics()
    print("places:", len(PLACES), "categories:", len(CATEGORIES))
    posts = []
    for i in range(count):
        title = f"【示例】{TITLES[i % len(TITLES)]}（{i + 1}）"
        author = names[i % len(names)]
        # 大部分带 1-2 个地点，部分只带分类，用来验证“无地点”路径
        mode = i % 7
        if mode == 6:
            cat = rnd.choice(CATEGORIES)
            topics = [{"topicId": cat[1]}]
        elif mode in (4, 5):
            p1 = rnd.choice(PLACES)
            p2 = rnd.choice(PLACES)
            topics = [{"topicId": p1[0], "sortNo": 0}, {"topicId": p2[0], "sortNo": 1}]
        else:
            p1 = rnd.choice(PLACES)
            topics = [{"topicId": p1[0], "sortNo": 0}]
        payload = {"title": title, "content": BODIES[i % len(BODIES)] + f" 第 {i + 1} 条示例。", "topics": topics}
        if images and i % 3 == 0:
            payload["media"] = [{"type": "IMAGE", "url": images[i % len(images)], "sortNo": 0}]
        st, body = call("/posts", payload, tokens[author])
        if st == 200:
            posts.append(body["data"]["id"])
    print("posts created:", len(posts))

    # 评论（顶层 + 回复）
    for idx, pid in enumerate(posts):
        author = names[idx % len(names)]
        others = [n for n in names if n != author]
        for j in range(rnd.randint(1, 3)):
            commenter = others[j % len(others)]
            st, body = call(f"/posts/{pid}/comments", {"content": rnd.choice(COMMENTS)}, tokens[commenter])
            if st == 200 and j == 0:
                call(f"/posts/{pid}/comments", {"content": "同问，蹲一个答案。", "parentId": body["data"]["id"]},
                     tokens[author])

    # 点赞 / 收藏 / 关注
    for idx, pid in enumerate(posts):
        for name in names:
            if rnd.random() < 0.5:
                call(f"/posts/{pid}/like", {}, tokens[name])
            if rnd.random() < 0.3:
                call(f"/posts/{pid}/collect", {}, tokens[name])
    for a in names:
        for b in names:
            if a != b and rnd.random() < 0.5:
                call(f"/users/{USERS.index(b) + 2}/follow", {}, tokens[a])

    # 评分 + 评价（部分带图）
    for topic_id, _ in PLACES[:20]:
        raters = rnd.sample(names, rnd.randint(1, 3))
        for name in raters:
            payload = {"score": rnd.randint(3, 5), "review": rnd.choice(REVIEWS)}
            if images and rnd.random() < 0.3:
                payload["images"] = [images[rnd.randrange(len(images))]]
            call(f"/topics/{topic_id}/ratings", payload, tokens[name], method="PUT")
    print("done")


if __name__ == "__main__":
    main(int(sys.argv[1]) if len(sys.argv) > 1 else 40)
