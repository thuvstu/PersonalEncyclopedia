package com.thuvstu.personalencyclopedia.db

import com.thuvstu.personalencyclopedia.db.dao.*
import com.thuvstu.personalencyclopedia.db.entity.*
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * 初期データ拡充 (walkthrough14以降): 高校古典/数学/英語 + 高等教育 地歴/法/経済 のガチ学問。
 * 本番DemoDataとは別に、初回起動時の空DBに「使い始めやすい」核を作る。
 * 体系: 各定義は【定義→体系的位置→例】の3層で記述し、前提(prerequisite)/関連/対比の接続でカリキュラムを編む。Wikiがハブ、クイズが定着を担う。
 * - 合計 125件の定義 + 思考6 + クイズ30 + Wiki6 + 接続20（★#U1: 実測125件に訂正）
 * - 本投入は sqlite-vec 検証完了後に段階的に行うが、カリキュラム設計とデータは先に確定する
 */
object InitialData {

    data class Def(val term: String, val reading: String?, val definition: String, val field: String, val topicId: String)

    // トピックは DemoData.topics に追加される想定。ここではIDだけ定義
    val extraTopics = listOf(
        TopicEntity(id = "topic-koten", name = "古典", colorHex = "#EC4899"),
        TopicEntity(id = "topic-math", name = "数学", colorHex = "#06B6D4"),
        TopicEntity(id = "topic-english", name = "英語", colorHex = "#F97316"),
        TopicEntity(id = "topic-law", name = "法学", colorHex = "#6366F1"),
    )

    val definitions = listOf(
        // 高校古典 20件
        Def("枕草子", "まくらのそうし", "【定義】清少納言の平安随筆300段。四季・随想・類聚。【体系】女房文学の頂点→徒然草・方丈記へ。【例】雪のいと高う降りたるを…で機知を示す。", "古典", "topic-koten"),
        Def("源氏物語", "げんじものがたり", "紫式部による平安長編物語。光源氏の生涯と宮廷社会を54帖で描く。もののあはれと女君たちの心理が核。", "古典", "topic-koten"),
        Def("徒然草", "つれづれぐさ", "兼好法師の鎌倉末期随筆。つれづれなるままに…で始まる243段。無常観と数寄を説く。", "古典", "topic-koten"),
        Def("平家物語", "へいけものがたり", "祇園精舎の鐘の声…で始まる軍記。平家一門の栄枯盛衰を琵琶法師が語る。", "古典", "topic-koten"),
        Def("万葉集", "まんようしゅう", "7-8世紀の歌集4500余首。額田王・柿本人麻呂・山上憶良ら。天皇から庶民まで幅広い作者。", "古典", "topic-koten"),
        Def("古今和歌集", "こきんわかしゅう", "905年紀貫之ら撰の勅撰第一集。仮名序・真名序を持ち古今の歌学の規範。", "古典", "topic-koten"),
        Def("伊勢物語", "いせものがたり", "在原業平を思わせる男を主人公とする歌物語125段。昔男ありけりで始まる。", "古典", "topic-koten"),
        Def("竹取物語", "たかとりものがたり", "かぐや姫をめぐる最古の物語。竹取の翁が月に帰る姫を描く。", "古典", "topic-koten"),
        Def("土佐日記", "とさにっき", "紀貫之が女手で綴った土佐からの帰京日記。仮名日記の嚆矢。", "古典", "topic-koten"),
        Def("更級日記", "さらしなにっき", "菅原孝標女が少女期の物語憧憬と晩年の回想を記した平安日記。", "古典", "topic-koten"),
        Def("方丈記", "ほうじょうき", "鴨長明が方丈の庵で無常を説く随筆。ゆく河の流れは絶えずして…。", "古典", "topic-koten"),
        Def("奥の細道", "おくのほそみち", "松尾芭蕉の元禄期紀行。月日は百代の過客にして…。曾良を伴い東北を巡る。", "古典", "topic-koten"),
        Def("助動詞「べし」", "じょどうしべし", "推量・意志・当然・命令・可能・適当の6義。終止形はべし、已然形はべけれ。", "古典文法", "topic-koten"),
        Def("敬語(尊敬・謙譲・丁寧)", "けいご", "古文敬語は誰から誰への敬意かで尊敬/謙譲/丁寧を区別。給ふ・奉る等の識別が読解の鍵。", "古典文法", "topic-koten"),
        Def("係り結び", "かかりむすび", "ぞ・なむ・や・か は連体形、こそは已然形で結ぶ。強調と係助詞の呼応。", "古典文法", "topic-koten"),
        Def("本歌取り", "ほんかどり", "古歌の一部を取り込み新歌に詠み込む技法。幽玄と有心の二重奏。", "古典", "topic-koten"),
        Def("物名", "ものな", "枕草子等に見られる言葉遊びの一種。物の名を隠して詠む。", "古典", "topic-koten"),
        Def("歌枕", "うたまくら", "和歌に詠み込まれる名所の枕詞的用法。吉野・龍田川など。", "古典", "topic-koten"),
        Def("和歌の修辞", "わかのしゅうじ", "枕詞・序詞・掛詞・縁語・本歌取り。歌の技巧の総称。", "古典", "topic-koten"),
        Def("説話集", "せつわしゅう", "今昔物語集・宇治拾遺物語など。仏教説話と世俗譚を収める。", "古典", "topic-koten"),
        // 数学 20件
        Def("二次関数", null, "y=ax^2+bx+c。頂点(-b/2a, -D/4a)、判別式D=b^2-4acで解の個数が決まる。", "数学", "topic-math"),
        Def("三角比", null, "sin, cos, tan。単位円と直角三角形で定義。正弦定理・余弦定理へ展開。", "数学", "topic-math"),
        Def("指数・対数", null, "a^x と log_a x は逆関数。底の変換公式 log_a b = ln b / ln a。", "数学", "topic-math"),
        Def("微分", null, "【定義】平均変化率の極限 f'(x)=lim h→0 (f(x+h)-f(x))/h。【体系】極限→微分→積分→微分方程式。【例】x^2の微分は2xで接線の傾き。", "数学", "topic-math"),
        Def("積分", null, "微分の逆演算。定積分は面積、原始関数の差で求まる(微積分学の基本定理)。", "数学", "topic-math"),
        Def("数列", null, "等差 a_n=a1+(n-1)d、等比 a_n=a1 r^{n-1}。Σ公式と極限が頻出。", "数学", "topic-math"),
        Def("ベクトル", null, "有向線分。内積 a·b=|a||b|cosθ、平面・空間の図形処理に有効。", "数学", "topic-math"),
        Def("確率", null, "場合の数、条件付き確率 P(A|B)=P(A∩B)/P(B)、期待値、分散。", "数学", "topic-math"),
        Def("集合と論理", null, "ド・モルガン、必要十分、対偶。ベン図と真理表で整理。", "数学", "topic-math"),
        Def("複素数平面", null, "z=x+yi、極形式 r(cosθ+i sinθ)、ド・モワブル。回転と拡大を表す。", "数学", "topic-math"),
        Def("行列(発展)", null, "線形写像の表現。行列式、逆行列、固有値は大学への橋渡し。", "数学", "topic-math"),
        Def("極限", null, "数列・関数の収束、はさみうち、ε-δの直観。", "数学", "topic-math"),
        Def("整数の性質", null, "ユークリッド互除法、合同式 mod、1次不定方程式。", "数学", "topic-math"),
        Def("図形と方程式", null, "直線・円・軌跡、領域の不等式。座標で幾何を代数化。", "数学", "topic-math"),
        Def("データの分析", null, "平均・分散・標準偏差・相関、箱ひげ図。", "数学", "topic-math"),
        Def("場合の数", null, "順列 P、組合せ C、重複組合せ、二項定理。", "数学", "topic-math"),
        Def("二次曲線", null, "放物線・楕円・双曲線の標準形と焦点・準線。", "数学", "topic-math"),
        Def("複素数と方程式", null, "解と係数、剰余の定理・因数定理、虚数解の共役。", "数学", "topic-math"),
        Def("式と証明", null, "恒等式、等式・不等式の証明、相加相乗。", "数学", "topic-math"),
        Def("統計的推測(発展)", null, "標本平均、信頼区間、仮説検定の考え方。", "数学", "topic-math"),
        // 英語 20件
        Def("仮定法", null, "If I were you... 現在の非現実は過去形、過去の非現実は had p.p.。", "英語", "topic-english"),
        Def("関係詞", null, "who/which/that/what、関係副詞 where/when/why。制限/非制限の区別。", "英語", "topic-english"),
        Def("不定詞", null, "to do の名詞/形容詞/副詞用法。It is ... to ... 構文。", "英語", "topic-english"),
        Def("分詞", null, "現在分詞 -ing(能動・進行)、過去分詞 -ed(受動・完了)。分詞構文へ。", "英語", "topic-english"),
        Def("動名詞", null, "V-ing の名詞用法。enjoy -ing / avoid -ing。to不定詞との使い分け。", "英語", "topic-english"),
        Def("助動詞", null, "can/may/must/should の推量・許可・義務。have to との違い。", "英語", "topic-english"),
        Def("比較", null, "原級・比較級・最上級、as ... as、no more than、倍数表現。", "英語", "topic-english"),
        Def("否定", null, "not、never、hardly、no/none、部分否定・全部否定。", "英語", "topic-english"),
        Def("倒置", null, "Never have I ...、So do I、Here comes ...。強調とバランス。", "英語", "topic-english"),
        Def("強調構文", null, "It is ... that ...、What ... is ...。焦点化の技法。", "英語", "topic-english"),
        Def("無生物主語", null, "主語が物でも訳は人に。The study shows ... = 調査によれば。", "英語", "topic-english"),
        Def("時制の一致", null, "主節過去なら従属節も過去へ。仮定法や普遍真理は例外。", "英語", "topic-english"),
        Def("態", null, "能動・受動、by以外(known to, covered with)、無生物主語の受動。", "英語", "topic-english"),
        Def("接続詞", null, "等位(and/or/but)と従位(when/because/if)。等位接続詞の省略に注意。", "英語", "topic-english"),
        Def("前置詞", null, "at/in/on の空間・時間、to/for/of の核心イメージ。", "英語", "topic-english"),
        Def("冠詞", null, "a/an/the/無冠詞。可算・不可算、定・不定の判断。", "英語", "topic-english"),
        Def("5文型", null, "SVOO/SVOC の見抜き方。OとCの品詞、Vの型が鍵。", "英語", "topic-english"),
        Def("分詞構文", null, "接続詞+主語を省略した分詞の副詞用法。Having p.p.で完了を表す。", "英語", "topic-english"),
        Def("関係詞の省略", null, "目的格の関係代名詞は省略可。There is ... 構文での that 省略にも注意。", "英語", "topic-english"),
        Def("語法(頻出動詞)", null, "provide A with B / prevent A from -ing / require that S (should) do。", "英語", "topic-english"),
        // 地歴(高等教育レベル) 20件
        Def("荘園制", "しょうえんせい", "奈良〜中世の寄進地系荘園と初期荘園。不輸・不入で国衙を空洞化。", "日本史", "topic-history"),
        Def("幕藩体制", "ばくはんたいせい", "江戸の将軍-大名-幕領の統治構造。参勤交代・軍役・石高制が柱。", "日本史", "topic-history"),
        Def("地租改正", "ちそかいせい", "1873年地価3%の金納地子。地券交付と地価決定で近代財政の基盤。", "日本史", "topic-history"),
        Def("大陸棚", null, "大陸縁辺の浅海部。領海・EEZ・大陸棚限界の海洋法上の区分。", "地理", "topic-history"),
        Def("国民国家", null, "主権・領域・国民の三要素と国民統合。ウェストファリア以降の近代国家像。", "世界史", "topic-history"),
        Def("帝国主義", null, "19世紀後半の列強の植民地分割。Leninの帝国主義論と世界システム。", "世界史", "topic-history"),
        Def("冷戦構造", null, "1947-89の米ソ二極と代理戦争・核抑止。脱植民地化と第三世界の台頭。", "世界史", "topic-history"),
        Def("地形と気候", null, "プレート境界・偏西風・モンスーン。日本の多様な気候区分の要因。", "地理", "topic-history"),
        Def("産業立地", null, "ウェーバーの工業立地論、チューネンの農業立地論。輸送費と集積。", "地理", "topic-history"),
        Def("人口転換", null, "多産多死→多産少死→少産少死。人口ピラミッドと高齢化の力学。", "地理", "topic-history"),
        Def("律令制", "りつりょうせい", "大宝律令・養老律令による中央集権。班田収授と戸籍・計帳が支える。", "日本史", "topic-history"),
        Def("摂関政治", "せっかんせいじ", "藤原氏が外戚として摂政・関白で権力を握る。荘園寄進が進む。", "日本史", "topic-history"),
        Def("院政", "いんせい", "上皇が院庁で政治を執る。荘園整理と武士の台頭を招く。", "日本史", "topic-history"),
        Def("鎖国", "さこく", "1639年ポルトガル船来航禁止、出島のオランダ・中国のみ交易。幕藩の統制強化。", "日本史", "topic-history"),
        Def("明治の地租と殖産興業", null, "地租改正と官営模範工場、鉄道・電信の敷設。富国強兵の二本柱。", "日本史", "topic-history"),
        Def("戦後改革", "せんごかいかく", "農地改革・財閥解体・労働改革・教育改革。民主化と非軍事化。", "日本史", "topic-history"),
        Def("高度経済成長", null, "1955-73年、年率10%成長。重化学工業化、所得倍増計画、オイルショックで終焉。", "日本史", "topic-history"),
        Def("絶対王政", null, "17-18世紀仏のルイ14世に典型。官僚制・常備軍・重商主義で集権。", "世界史", "topic-history"),
        Def("市民革命", null, "英名誉革命・米独立・仏革命。自然権と国民主権を掲げる。", "世界史", "topic-history"),
        Def("産業革命", null, "18世紀英の綿工業・蒸気機関から始まる機械制生産への転換。", "世界史", "topic-history"),
        Def("民族自決", null, "第一次大戦後のウィルソン原則。帝国解体と国民国家の波を生む。", "世界史", "topic-history"),
        Def("地中海世界", null, "フェニキア・ギリシア・ローマを結ぶ交易圏。オリーブと小麦の文明。", "世界史", "topic-history"),
        Def("中華王朝の変遷", null, "秦漢から明清まで。科挙・官僚制・朝貢体制が特徴。", "世界史", "topic-history"),
        Def("ケッペン気候区分", null, "A熱帯〜E寒帯。降水・気温で世界を区分。植生と対応。", "地理", "topic-history"),
        Def("資源とエネルギー", null, "化石燃料・レアメタル・再生可能エネルギー。地政学と結びつく。", "地理", "topic-history"),
        // 法 20件
        Def("日本国憲法の三原理", null, "国民主権・基本的人権の尊重・平和主義。前文と9条が核。", "憲法", "topic-law"),
        Def("立憲主義", null, "憲法が国家権力を拘束する思想。法の支配と人権保障の前提。", "憲法", "topic-law"),
        Def("所有権", null, "民法206条の使用収益処分権。物権の排他性と対抗要件(177条)。", "民法", "topic-law"),
        Def("契約自由の原則", null, "締結・相手方・内容・方式の自由。強行法規・公序良俗で制約。", "民法", "topic-law"),
        Def("不法行為", null, "709条の故意過失・権利侵害・損害・因果関係。損害賠償の一般条項。", "民法", "topic-law"),
        Def("罪刑法定主義", null, "刑法の根本。法律なければ犯罪なし・刑罰なし。遡及処罰の禁止。", "刑法", "topic-law"),
        Def("株式会社", null, "株主有限責任・所有と経営の分離。機関設計(取締役会/監査役)。", "会社法", "topic-law"),
        Def("行政裁量", null, "法規裁量と便宜裁量。裁量の逸脱濫用は司法審査の対象。", "行政法", "topic-law"),
        Def("司法審査制", null, "違憲立法審査権(81条)。付随的審査制と抽象的審査の比較。", "憲法", "topic-law"),
        Def("信義則", null, "民法1条2項。権利濫用との関係、禁反言・クリーンハンズ。", "民法", "topic-law"),
        Def("物権変動", null, "177条の対抗要件主義。不動産は登記、動産は引渡し。", "民法", "topic-law"),
        Def("担保物権", null, "抵当権・質権・留置権。優先弁済と留置の違い。", "民法", "topic-law"),
        Def("相続", null, "法定相続分・遺留分・遺言。2024年相続登記義務化も押さえる。", "民法", "topic-law"),
        Def("不作為犯", null, "作為義務ある不作為が構成要件を満たす。保障人的地位が鍵。", "刑法", "topic-law"),
        Def("共犯", null, "共同正犯・教唆・幇助。正犯意思と従属性の区別。", "刑法", "topic-law"),
        Def("法人税", null, "法人所得への課税。益金・損金の算定、租税特別措置。", "税法", "topic-law"),
        Def("労働法の二本柱", null, "労働基準法(最低基準)と労働契約法(合意原則)。", "労働法", "topic-law"),
        Def("独占禁止法", null, "私的独占・不当な取引制限・不公正な取引方法の3本柱。", "経済法", "topic-law"),
        Def("裁判員制度", null, "2009年開始。国民が重大刑事事件の審理に参加。", "司法", "topic-law"),
        Def("国際法の法源", null, "条約・慣習・法の一般原則。ICJ規程38条。", "国際法", "topic-law"),
        // 経済 20件
        Def("GDP", null, "【定義】国内総生産。付加価値の合計。三面等価(生産=分配=支出)。【体系】GDP→IS-LM→財政金融政策。【例】名目/実質=GDPデフレーター。", "経済", "topic-economy"),
        Def("IS-LM分析", null, "財市場(IS)と貨幣市場(LM)の同時均衡。財政・金融政策の効果を図示。", "経済", "topic-economy"),
        Def("比較優位", null, "機会費用が低い財に特化。リカードの貿易モデル、交易条件。", "経済", "topic-economy"),
        Def("外部性", null, "市場で価格付けされない便益/費用。ピグー税・補助金、コースの定理。", "経済", "topic-economy"),
        Def("独占と寡占", null, "価格支配力、クールノー・ベルトラン競争、ゲーム理論との接続。", "経済", "topic-economy"),
        Def("財政政策", null, "政府支出・租税による需要管理。乗数効果、クラウディングアウト。", "経済", "topic-economy"),
        Def("金融政策", null, "金利・マネーサプライ操作。テイラールール、量的緩和。", "経済", "topic-economy"),
        Def("為替レート", null, "固定/変動、マンデル=フレミング、購買力平価と金利平価。", "経済", "topic-economy"),
        Def("労働市場", null, "賃金決定、失業の類型(摩擦的/構造的/循環的)、フィリップス曲線。", "経済", "topic-economy"),
        Def("社会保障", null, "年金・医療・介護の財政、賦課方式と積立方式、少子高齢化の負担。", "経済", "topic-economy"),
        Def("景気循環", null, "キチン・ジュグラー・クズネッツ・コンドラチェフの4循環。", "経済", "topic-economy"),
        Def("公共財", null, "非競合・非排除性。フリーライドと政府供給の根拠。", "経済", "topic-economy"),
        Def("ゲーム理論", null, "囚人のジレンマ、ナッシュ均衡。寡占・交渉の分析に。", "経済", "topic-economy"),
        Def("行動経済学", null, "限定合理性、プロスペクト理論、ナッジ。", "経済", "topic-economy"),
        Def("国際収支", null, "経常収支・資本収支・外貨準備。貯蓄投資バランス。", "経済", "topic-economy"),
        Def("インフレ目標", null, "2%物価安定目標、期待への働きかけ。", "経済", "topic-economy"),
        Def("税制", null, "所得税・法人税・消費税の三税、累進と逆進、タックスヘイブン。", "経済", "topic-economy"),
        Def("格差と再分配", null, "ジニ係数、累進課税・社会保障による再分配、機会の平等。", "経済", "topic-economy"),
        Def("経済成長論", null, "ソロー・モデル、定常状態、技術進歩と人的資本。", "経済", "topic-economy"),
        Def("計量経済学入門", null, "回帰分析、最小二乗法、因果推論の基礎。", "経済", "topic-economy"),
    )

    // 思考: 各分野の学び方メモ
    val thoughts = listOf(
        DemoData.DemoThought("古典は音で覚える", "音読と暗唱でリズムを体に入れる。助動詞の活用は歌にしてしまうのが早い。"),
        DemoData.DemoThought("数学は手を動かす", "定義を写し例題を解き、なぜそうなるかを自分の言葉で書く。1問を3回解く。"),
        DemoData.DemoThought("英語は文型から", "5文型と品詞が分かれば長い文も切れる。主語と動詞をまず見つける。"),
        DemoData.DemoThought("地歴は地図と年表", "地図に書き込み、年表に並べる。空間と時間の2軸で記憶は定着する。"),
        DemoData.DemoThought("法学は条文に帰る", "判例を読む前に条文を読む。要件・効果・趣旨の三点セットで整理。"),
        DemoData.DemoThought("経済は図で考える", "IS-LMも需給も図を描いて矢印で因果を追う。数式は図の翻訳。"),
    )

    val quizzes = listOf(
        DemoData.DemoQuiz("枕草子「春はあけぼの」の季節は？", "春", listOf("春","夏","秋","冬"), "春はあけぼの…で始まることから。"),
        DemoData.DemoQuiz("源氏物語の作者は？", "紫式部", listOf("紫式部","清少納言","兼好法師","鴨長明"), "紫式部が宮廷で執筆。"),
        DemoData.DemoQuiz("係り結びで「こそ」に呼応する活用形は？", "已然形", listOf("已然形","連体形","終止形","命令形"), "こそは已然形、他は連体形。"),
        DemoData.DemoQuiz("二次関数の頂点のx座標は？", "-b/2a", listOf("-b/2a","-b/a","b/2a","-4a/b"), "平方完成で導出。"),
        DemoData.DemoQuiz("sin30°の値は？", "1/2", listOf("1/2","√3/2","1/√2","1"), "単位円で確認。"),
        DemoData.DemoQuiz("log_2 8 の値は？", "3", listOf("3","2","4","8"), "2^3=8。"),
        DemoData.DemoQuiz("微分係数の定義は？", "平均変化率の極限", listOf("平均変化率の極限","面積","平均値","分散"), "h→0の極限。"),
        DemoData.DemoQuiz("仮定法で If I were you の were は？", "仮定法過去", listOf("仮定法過去","直説法過去","仮定法過去完了","未来"), "現在の非現実は過去形。"),
        DemoData.DemoQuiz("関係代名詞 that が省略できるのは？", "目的格", listOf("目的格","主格","所有格","関係副詞"), "目的格は省略可。"),
        DemoData.DemoQuiz("5文型で SVOO の Oは？", "目的語が2つ", listOf("目的語が2つ","補語が1つ","修飾語","主語"), "S give O1 O2。"),
        DemoData.DemoQuiz("荘園の不輸・不入とは？", "租税・国司立入の免除", listOf("租税・国司立入の免除","年貢免除のみ","軍役免除","検地免除"), "国衙の支配が及ばない。"),
        DemoData.DemoQuiz("地租改正の税率は？", "地価の3%", listOf("地価の3%","収穫の3%","地価の5%","定額"), "1873年金納化。"),
        DemoData.DemoQuiz("ウェストファリア条約の年は？", "1648年", listOf("1648年","1789年","1919年","1945年"), "主権国家体制の起点。"),
        DemoData.DemoQuiz("日本国憲法の三原理に含まれないのは？", "議院内閣制", listOf("議院内閣制","国民主権","平和主義","基本的人権"), "三原理は国民主権・人権・平和主義。"),
        DemoData.DemoQuiz("罪刑法定主義の意味は？", "法律なければ犯罪なし", listOf("法律なければ犯罪なし","疑わしきは罰せず","一事不再理","法の不遡及"), "nulla poena sine lege。"),
        DemoData.DemoQuiz("所有権の権能は？", "使用収益処分", listOf("使用収益処分","占有のみ","賃貸のみ","売却のみ"), "民法206条。"),
        DemoData.DemoQuiz("GDPの三面等価に含まれないのは？", "幸福度", listOf("幸福度","生産","分配","支出"), "生産=分配=支出。"),
        DemoData.DemoQuiz("比較優位の提唱者は？", "リカード", listOf("リカード","スミス","ケインズ","マルクス"), "機会費用の概念。"),
        DemoData.DemoQuiz("IS-LMで財政拡大は？", "IS右シフト", listOf("IS右シフト","LM右シフト","IS左シフト","LM左シフト"), "財市場需要増。"),
        DemoData.DemoQuiz("外部性の是正手段は？", "ピグー税", listOf("ピグー税","関税","所得税","消費税"), "外部費用を内部化。"),
        DemoData.DemoQuiz("徒然草の作者は？", "兼好法師", listOf("兼好法師","清少納言","紫式部","鴨長明"), "吉田兼好。"),
        DemoData.DemoQuiz("方丈記の冒頭は？", "ゆく河の流れは絶えずして", listOf("ゆく河の流れは絶えずして","春はあけぼの","祇園精舎の鐘の声","月日は百代の過客にして"), "無常観の象徴。"),
        DemoData.DemoQuiz("助動詞「べし」の已然形は？", "べけれ", listOf("べけれ","べし","べく","べから"), "文法の基本。"),
        DemoData.DemoQuiz("積分の基本定理は？", "微分の逆演算で面積", listOf("微分の逆演算で面積","極限","順列","分散"), "Newton-Leibniz。"),
        DemoData.DemoQuiz("ベクトルの内積は？", "|a||b|cosθ", listOf("|a||b|cosθ","|a|+|b|","|a|-|b|","|a|×|b|"), "角度で定義。"),
        DemoData.DemoQuiz("倒置が起きるのは？", "Never文頭", listOf("Never文頭","肯定文","疑問文のみ","命令文"), "Never have I..."),
        DemoData.DemoQuiz("幕藩体制の石高制とは？", "米の生産高で格付け", listOf("米の生産高で格付け","人口で格付け","面積で格付け","税額で格付け"), "石高が身分と軍役の基準。"),
        DemoData.DemoQuiz("ケッペン気候でAは？", "熱帯", listOf("熱帯","乾燥帯","温帯","寒帯"), "最暖月18℃以上。"),
        DemoData.DemoQuiz("株式会社の特徴は？", "株主有限責任", listOf("株主有限責任","無限責任","合名のみ","個人事業"), "所有と経営の分離。"),
        DemoData.DemoQuiz("ナッシュ均衡とは？", "誰も単独で逸脱する誘因なし", listOf("誰も単独で逸脱する誘因なし","全員が最大利得","政府が介入","独占"), "ゲーム理論の核心。"),
    )

    val wikis = listOf(
        WikiArticleEntity(title = "古典文法クイックリファレンス", contentMd = "# 古典文法クイックリファレンス\n\n- [[助動詞「べし」]] / [[係り結び]] / [[敬語(尊敬・謙譲・丁寧)]]\n- [[本歌取り]] と [[和歌の修辞]] は歌学の核\n- 演習: [[枕草子]] と [[源氏物語]] を読み比べる\n", summary = "古典文法の要点を1枚に"),
        WikiArticleEntity(title = "数学公式ハブ — 高校範囲", contentMd = "# 数学公式ハブ\n\n- [[二次関数]] / [[三角比]] / [[指数・対数]] / [[微分]] / [[積分]]\n- [[ベクトル]] と [[図形と方程式]] で幾何を代数化\n- [[統計的推測(発展)]] は共通テスト頻出\n", summary = "数学の相互リンク集"),
        WikiArticleEntity(title = "英語5文型と時制", contentMd = "# 英語5文型と時制\n\n- [[5文型]] → [[関係詞]] → [[分詞構文]]\n- [[仮定法]] と [[時制の一致]] はセットで\n- 無生物主語は和訳で人を補う\n", summary = "英語の骨格"),
        WikiArticleEntity(title = "日本史ストーリーライン", contentMd = "# 日本史ストーリーライン\n\n[[律令制]] → [[摂関政治]] → [[院政]] → [[荘園制]] → [[幕藩体制]] → [[地租改正]] → [[戦後改革]] → [[高度経済成長]]\n", summary = "通史の軸"),
        WikiArticleEntity(title = "法学マップ — 要件効果", contentMd = "# 法学マップ\n\n- 憲法: [[日本国憲法の三原理]] / [[立憲主義]] / [[司法審査制]]\n- 民事: [[所有権]] / [[契約自由の原則]] / [[不法行為]] / [[担保物権]]\n- 刑事: [[罪刑法定主義]]\n", summary = "条文→判例の道標"),
        WikiArticleEntity(title = "経済学コア — 図でわかる", contentMd = "# 経済学コア\n\n[[GDP]] → [[IS-LM分析]] → [[財政政策]] / [[金融政策]] → [[為替レート]]\n需給は [[比較優位]] と [[外部性]] で拡張\n", summary = "マクロ/ミクロの接続"),
    )

    val connections = listOf(
        Triple("枕草子", "徒然草", "related"), Triple("源氏物語", "伊勢物語", "related"), Triple("助動詞「べし」", "係り結び", "prerequisite"),
        Triple("二次関数", "微分", "prerequisite"), Triple("三角比", "ベクトル", "related"), Triple("指数・対数", "微分", "prerequisite"),
        Triple("仮定法", "時制の一致", "related"), Triple("関係詞", "分詞構文", "prerequisite"), Triple("5文型", "関係詞", "prerequisite"),
        Triple("荘園制", "幕藩体制", "related"), Triple("律令制", "摂関政治", "related"), Triple("地租改正", "高度経済成長", "related"),
        Triple("国民国家", "帝国主義", "related"), Triple("産業革命", "帝国主義", "prerequisite"), Triple("日本国憲法の三原理", "立憲主義", "related"),
        Triple("所有権", "担保物権", "related"), Triple("契約自由の原則", "不法行為", "contrast"), Triple("GDP", "IS-LM分析", "prerequisite"),
        Triple("比較優位", "為替レート", "related"), Triple("外部性", "公共財", "related"),
    )

    suspend fun seedIfEmpty(
        entryDao: EntryDao, thoughtDao: EntryThoughtDao, definitionDao: EntryDefinitionDao,
        topicDao: TopicDao? = null, quizDao: QuizDao? = null, connectionDao: ConnectionDao? = null, wikiDao: WikiArticleDao? = null
    ) {
        val count = entryDao.observeCount().first()
        if (count > 0) return
        val now = System.currentTimeMillis()
        extraTopics.forEach { topicDao?.insert(it) }
        val idMap = mutableMapOf<String, String>()
        for (d in definitions) {
            val id = UUID.randomUUID().toString()
            idMap[d.term] = id
            entryDao.insert(EntryEntity(id = id, type = "definition", title = d.term, createdAt = now, updatedAt = now, accessedAt = now))
            definitionDao.insert(EntryDefinitionEntity(entryId = id, term = d.term, reading = d.reading, definition = d.definition, field = d.field))
        }
        for (t in thoughts) {
            val id = UUID.randomUUID().toString()
            idMap[t.title] = id
            entryDao.insert(EntryEntity(id = id, type = "thought", title = t.title, content = t.content, createdAt = now, updatedAt = now, accessedAt = now))
            thoughtDao.insert(EntryThoughtEntity(entryId = id))
        }
        for (q in quizzes) {
            val choicesJson = "[" + q.choices.joinToString(",") { "\"$it\"" } + "]"
            quizDao?.insertQuiz(QuizBankEntity(question = q.question, answer = q.answer, choicesJson = choicesJson, explanation = q.explanation, quizType = q.quizType, generationMethod = "initial"))
        }
        for (w in wikis) wikiDao?.upsert(w)
        for ((a, b, rel) in connections) {
            val aId = idMap[a] ?: continue; val bId = idMap[b] ?: continue
            val ca = if (aId < bId) aId else bId; val cb = if (aId < bId) bId else aId
            try { connectionDao?.insert(ConnectionEntity(entryAId = aId, entryBId = bId, relationType = rel, strength = 0.7f, isAuto = false, isDirected = false, canonicalA = ca, canonicalB = cb)) } catch (_: Exception) {}
        }
    }
}
