"""roam-bupt 开发用种子内容：通过真实接口注册作者并发布帖子。

用法（后端已在 8080 启动）：
    python scripts/seed_posts.py

幂等：作者已存在时自动登录；帖子重复不会去重（重复跑会多出一批），
需要重灌时先清空 post/post_topic 两张表。
"""
import json
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080/api/v1"

AUTHORS = [
    ("seed_lin", "secret123", "林知远"),
    ("seed_chen", "secret123", "陈默"),
    ("seed_zhou", "secret123", "周予安"),
    ("seed_tang", "secret123", "阿糖"),
]

# 地点 id（见 seed 数据）：79 教三楼 / 80 教二楼 / 81 教四楼 / 89 图书馆 / 90 主教学楼
# 113 学生食堂 / 114 综合食堂 / 112 风味餐厅 / 107 麦当劳 / 93 体育场 / 77 体育馆
# 94 超市 / 109 瑞幸 / 111 打印店 / 115 水房 / 78 科学会堂 / 50 校训石 / 40 学生5号公寓
# 分类 id：1001 食堂 / 1002 自习 / 1003 住宿 / 1004 运动 / 1005 教学楼 / 1006 服务 / 1007 地标
POSTS = [
    (0, "教三四楼靠窗那排插座，晚上七点以后基本没位子",
     "连着蹲了一周，四楼东侧靠窗一共 32 个座位，19:00 之前到基本能坐满一半。想安静写代码的话，五楼走廊尽头那两张桌子反而没人抢。", [79, 1002]),
    (1, "从学十走到图书馆，我摸出一条下雨天不用跑的路",
     "平时五分钟的路，下雨天从教二后门进去，走二层连廊到东侧，再从侧门下到图书馆北门。全程要么在屋檐下，要么在连廊里，只有两处十几米露天。", [40, 80, 89]),
    (3, "新生第一周：这五个地方先认全，剩下慢慢来",
     "报到、体检、领卡、开网、找教室，五件事对应五个地方。照着走大概四十分钟，别在报到那天绕校园。", [90, 50, 94]),
    (2, "四楼饮水机在哪一层换过滤芯，终于有准信了",
     "后勤说三层和五层是两周一次，四层是一周一次，因为人最多。水房门口贴了表。", [79, 1006]),
    (1, "晚上十点半以后还有哪间教室开着灯",
     "一层东侧 101、102 是不关的，但十点半后保安会巡一次。二层往上十点二十就开始赶人。", [79, 1002]),
    (2, "学一三楼新窗口，六点去排队最短",
     "砂锅和拌饭都比一楼稳，六点前面五六个人，六点半以后要排到楼梯口。", [113, 1001]),
    (0, "教三五楼最里面那间教室，晚上几乎没人",
     "508 只有两个班在这儿排过课，晚上基本空着。插座有六个，暖气比楼下足，缺点是离厕所远。", [79, 1002]),
    (1, "图书馆四楼靠窗的位置，几点开始抢",
     "八点开门，八点十分就只剩中间。想坐窗边建议七点五十到门口等。", [89, 1002]),
    (3, "综合食堂的砂锅和风味餐厅的拌饭，哪个更值",
     "砂锅 15 块管饱，拌饭 18 但料足。赶时间选砂锅，想吃饱选拌饭。", [114, 112, 1001]),
    (2, "麦当劳晚上十一点还开，赶 due 的时候救过命",
     "两点关门，十一点以后人少、出餐快。别忘了带学生卡，第二份半价。", [107, 1001]),
    (0, "体育场晚上八点以后跑道上人少",
     "七点到八点是高峰，基本是散步的人；八点半以后才能正经跑步。", [93, 1004]),
    (1, "瑞幸早上几点不用排队",
     "七点四十之前下单基本立取，八点后就排到门口了。", [109, 1006]),
    (3, "打印店哪家便宜、哪家能打彩印",
     "西门那家黑白一毛、彩印一块；教学楼下面那家贵两毛但能装订。", [111, 1006]),
    (2, "水房热水几点到几点",
     "早上六点半到九点，晚上五点半到十一点。周末早上会晚半小时。", [115, 1006]),
    (0, "超市晚上十点后打折，面包半价",
     "临期的面包和饭团半价，酸奶七折。十点去刚好。", [94, 1006]),
    (1, "科学会堂的讲座一般在哪看通知",
     "公众号会发，但经常晚一天。楼门口的海报最准。", [78, 1007]),
    (3, "校训石旁边那条小路，晚上没灯",
     "白天抄近道很爽，晚上建议绕主路，石板还有点滑。", [50, 1007]),
    (2, "教二楼和教四楼的连廊能不能走通",
     "二楼连廊走通，但要绕到东侧楼梯；三楼是断的。", [80, 81, 1005]),
    (0, "主教学楼一层自习室，早上七点半开门",
     "七点半开门，八点前基本没人。插座在靠墙一排。", [90, 1002]),
    (1, "体育馆羽毛球场怎么约",
     "体育馆小程序提前三天放场，晚上七点后的场子最快没。", [77, 1004]),
    (3, "学生5号公寓楼下快递柜经常满",
     "下午五点到七点最满，建议上午取或者让快递放驿站。", [40, 1003]),
    (2, "食堂三楼窗口哪天休息",
     "三楼周一休息，二楼周三下午检修。", [113, 1001]),
    (0, "教四楼哪个教室有投影、哪个只有黑板",
     "101、203 有投影，其余大多是黑板。要用投影提前问教务处。", [81, 1005]),
    (1, "校园里最适合发呆的三个角落",
     "图书馆四楼靠窗、校训石后面的长椅、科学会堂门口的台阶。", [50, 89, 78, 1007]),
]


def call(method, path, payload=None, token=None):
    data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        return {"code": -1, "message": f"HTTP {e.code}: {body}"}


def login_or_register(username, password, nickname):
    result = call("POST", "/auth/register",
                  {"username": username, "password": password, "nickname": nickname})
    if result.get("code") == 0:
        return result["data"]["token"], "registered"
    if result.get("code") == 409:
        result = call("POST", "/auth/login", {"username": username, "password": password})
        if result.get("code") == 0:
            return result["data"]["token"], "logged in"
    raise SystemExit(f"作者 {username} 准备失败: {result}")


def main():
    tokens = []
    for username, password, nickname in AUTHORS:
        token, state = login_or_register(username, password, nickname)
        tokens.append(token)
        print(f"author {username}: {state}")

    ok = 0
    for index, (author_index, title, content, topic_ids) in enumerate(POSTS, start=1):
        payload = {
            "title": title,
            "content": content,
            "topics": [{"topicId": topic_id, "sortNo": position}
                       for position, topic_id in enumerate(topic_ids)],
        }
        result = call("POST", "/posts", payload, tokens[author_index])
        if result.get("code") == 0:
            ok += 1
            print(f"  [{index:02d}] post {result['data']['id']} {result['data']['status']} {title[:20]}")
        else:
            print(f"  [{index:02d}] FAILED {title[:20]}: {result}")
    print(f"done: {ok}/{len(POSTS)} posts created")


if __name__ == "__main__":
    main()
