// 契约 §4 PostVo / §5 CommentVo 的 fixture；时间格式 yyyy-MM-dd HH:mm:ss
const NOW = Date.now()
const pad = (value) => String(value).padStart(2, '0')

function formatTime(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function hoursAgo(hours) {
  return formatTime(new Date(NOW - hours * 60 * 60 * 1000))
}

const authorLin = { id: 1, nickname: '林知远', avatar: null, followed: false }
const authorZhou = { id: 2, nickname: '周予安', avatar: null, followed: false }
const authorChen = { id: 3, nickname: '陈默', avatar: null, followed: false }
const authorTang = { id: 4, nickname: '阿糖', avatar: null, followed: false }

export const posts = [
  {
    id: 1,
    title: '教三四楼靠窗那排插座，晚上七点以后基本没位子',
    excerpt:
      '连着蹲了一周，四楼东侧靠窗一共 32 个座位，19:00 之前到基本能坐满一半。想安静写代码的话，五楼走廊尽头那两张桌子反而没人抢。',
    status: 'ONLINE',
    author: authorLin,
    topics: [
      { id: 113, type: 'PLACE', name: '第三教学楼', sortNo: 0 },
      { id: 201, type: 'CATEGORY', name: '自习', sortNo: 0 },
    ],
    media: [],
    counts: { like: 28, collect: 12, comment: 31, view: 420 },
    myLiked: null,
    myCollected: null,
    createdTime: hoursAgo(2),
    updatedTime: hoursAgo(2),
  },
  {
    id: 2,
    title: '从学十走到图书馆，我摸出一条下雨天不用跑的路',
    excerpt: '平时五分钟的路，下雨天从教二后门绕，全程有屋檐或者连廊，多走一百多米但不淋。',
    status: 'ONLINE',
    author: authorChen,
    topics: [
      { id: 118, type: 'PLACE', name: '学十宿舍', sortNo: 0 },
      { id: 117, type: 'PLACE', name: '教二', sortNo: 1 },
      { id: 114, type: 'PLACE', name: '图书馆', sortNo: 2 },
      { id: 206, type: 'CATEGORY', name: '雨天', sortNo: 0 },
    ],
    media: [],
    counts: { like: 64, collect: 38, comment: 31, view: 1200 },
    myLiked: null,
    myCollected: null,
    createdTime: hoursAgo(25),
    updatedTime: hoursAgo(25),
  },
  {
    id: 3,
    title: '新生第一周：这五个地方先认全，剩下慢慢来',
    excerpt:
      '报到、体检、领卡、开网、找教室，五件事对应五个地方。附件里是我画的一条走完的顺序，照着走大概四十分钟。',
    status: 'ONLINE',
    author: authorTang,
    topics: [
      { id: 119, type: 'PLACE', name: '主楼', sortNo: 0 },
      { id: 205, type: 'CATEGORY', name: '新生', sortNo: 0 },
    ],
    media: [],
    counts: { like: 112, collect: 45, comment: 47, view: 2300 },
    myLiked: null,
    myCollected: null,
    createdTime: hoursAgo(50),
    updatedTime: hoursAgo(50),
  },
  {
    id: 4,
    title: '四楼饮水机在哪一层换过滤芯，终于有准信了',
    excerpt: '后勤的老师说三层和五层是两周一次，四层是一周一次，因为人最多。水房门口贴了表。',
    status: 'ONLINE',
    author: authorZhou,
    topics: [
      { id: 113, type: 'PLACE', name: '第三教学楼', sortNo: 0 },
      { id: 204, type: 'CATEGORY', name: '设施', sortNo: 0 },
    ],
    media: [],
    counts: { like: 15, collect: 4, comment: 6, view: 180 },
    myLiked: null,
    myCollected: null,
    createdTime: hoursAgo(3),
    updatedTime: hoursAgo(3),
  },
  {
    id: 5,
    title: '晚上十点半以后还有哪间教室开着灯',
    excerpt: '一层东侧 101、102 是不关的，但十点半后保安会巡一次。二层往上十点二十就开始赶人。',
    status: 'ONLINE',
    author: authorChen,
    topics: [
      { id: 113, type: 'PLACE', name: '第三教学楼', sortNo: 0 },
      { id: 201, type: 'CATEGORY', name: '自习', sortNo: 0 },
    ],
    media: [],
    counts: { like: 41, collect: 9, comment: 18, view: 760 },
    myLiked: null,
    myCollected: null,
    createdTime: hoursAgo(27),
    updatedTime: hoursAgo(27),
  },
  {
    id: 6,
    title: '学一三楼新窗口，六点去排队最短',
    excerpt: '砂锅和拌饭都比一层稳，六点前面五六个人，六点半以后要排到楼梯口。',
    status: 'ONLINE',
    author: authorZhou,
    topics: [
      { id: 115, type: 'PLACE', name: '学一食堂', sortNo: 0 },
      { id: 202, type: 'CATEGORY', name: '食堂', sortNo: 0 },
    ],
    media: [],
    counts: { like: 22, collect: 11, comment: 9, view: 340 },
    myLiked: null,
    myCollected: null,
    createdTime: hoursAgo(5),
    updatedTime: hoursAgo(5),
  },
]

// 列表不返回正文，详情才带 content，按契约分开存
export const postContents = {
  1: '四楼东侧靠窗那排桌子从早八点开始就陆续有人。我连着观察了一周，工作日 19:00 之前到还能坐满一半，19:00 以后基本没位子。插座在窗台下面，一排六个，坏了一个。\n\n如果只是想安静写代码，五楼走廊尽头那两张桌子反而没人抢，缺点是离饮水机远，接水要下一层。晚上十点以后四楼会赶人，一层东侧 101、102 不关。',
  2: '平时从学十到图书馆五分钟，但一下雨就只能举着书包跑。这学期我试了几条，最后定下来这一条：出宿舍往教二方向，从教二后门进去，走二层连廊到东侧，再从侧门下到图书馆北门。\n\n全程要么在屋檐下，要么在连廊里，只有两处十几米是露天的。代价是多走一百多米，慢一分钟。\n\n连廊这一段晚上十点会锁，别走太晚。另外雨天路滑，教二后门那截台阶有青苔，去年有人在那儿摔过。',
  3: '报到、体检、领卡、开网、找教室，五件事对应五个地方。顺序是主楼一层大厅报到，校医院体检，学一食堂门口领卡，图书馆四层开网，最后去教三找教室。\n\n照着走大概四十分钟，中间不用折返。体检要空腹的话，把校医院放到第一位，其他几件事往后挪。',
  4: '后勤的老师说三层和五层是两周一次，四层是一周一次，因为人最多。水房门口贴了表，换滤芯那天水流会小半小时。\n\n接水高峰在中午十二点和下午五点半，想快点就避开这两个点。',
  5: '一层东侧 101、102 是不关的，但十点半后保安会巡一次。二层往上十点二十就开始赶人，走之前记得把桌上的东西收走。\n\n通宵的话只有主楼一层大厅能坐，但没插座，也没暖气。',
  6: '三层新开的窗口卖砂锅和拌饭，六点去排队前面大概五六个人，六点半以后要排到楼梯口。\n\n推荐砂锅豆腐和鸡腿拌饭，量够，价格比一层贵三块左右。窗口只收校园卡，现金不行。',
}

export const commentsByPost = {
  1: [
    {
      id: 901,
      postId: 1,
      content: '五楼那两张桌子我上周去也有人了，可能是被这篇带过去的。',
      author: { id: 2, nickname: '周予安', avatar: null },
      parentId: 0,
      topId: 901,
      replyCount: 0,
      createdTime: hoursAgo(1),
    },
  ],
  2: [
    {
      id: 911,
      postId: 2,
      content: '教二后门那段确实滑，我去年摔过一次，现在宁可绕正门。',
      author: { id: 2, nickname: '周予安', avatar: null },
      parentId: 0,
      topId: 911,
      replyCount: 0,
      createdTime: hoursAgo(3),
    },
    {
      id: 912,
      postId: 2,
      content: '收藏了。顺便问下连廊是十点锁还是十点半？上次去好像还开着。',
      author: { id: 1, nickname: '林知远', avatar: null },
      parentId: 0,
      topId: 912,
      replyCount: 0,
      createdTime: hoursAgo(2),
    },
    {
      id: 913,
      postId: 2,
      content: '十点锁，十点半那次可能是保安还没巡到。',
      author: { id: 3, nickname: '陈默', avatar: null },
      parentId: 0,
      topId: 913,
      replyCount: 0,
      createdTime: hoursAgo(1),
    },
  ],
  4: [
    {
      id: 921,
      postId: 4,
      content: '表是打印的，上个月换过一次，现在贴的是新的。',
      author: { id: 1, nickname: '林知远', avatar: null },
      parentId: 0,
      topId: 921,
      replyCount: 0,
      createdTime: hoursAgo(2),
    },
  ],
}
