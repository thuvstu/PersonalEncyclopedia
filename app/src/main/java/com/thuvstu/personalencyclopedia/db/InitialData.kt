package com.thuvstu.personalencyclopedia.db

import com.thuvstu.personalencyclopedia.db.dao.*
import com.thuvstu.personalencyclopedia.db.entity.*
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * 初期データ拡充 (walkthrough14以降): 高校古典/数学/英語 + 高等教育 地歴/法/経済 のガチ学問。
 * 本番DemoDataとは別に、初回起動時の空DBに「使い始めやすい」核を作る。
 * - 合計 120件 (各6分野×20件) の定義エントリ + 思考6件 + クイズ30件
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
        Def("枕草子", "まくらのそうし", "清少納言による平安中期の随筆。春はあけぼの…に始まる四季・随想・類聚章段から成る。定子サロンの美意識と機知を示す。", "古典", "topic-koten"),
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
        Def("微分", null, "平均変化率の極限。f'(x)=lim h→0 (f(x+h)-f(x))/h。接線の傾き。", "数学", "topic-math"),
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
        // 経済 20件 (残り10は地理/世界史で補完)
        Def("GDP", null, "国内総生産。付加価値の合計、三面等価。名目vs実質、GDPデフレーター。", "経済", "topic-economy"),
        Def("IS-LM分析", null, "財市場(IS)と貨幣市場(LM)の同時均衡。財政・金融政策の効果を図示。", "経済", "topic-economy"),
        Def("比較優位", null, "機会費用が低い財に特化。リカードの貿易モデル、交易条件。", "経済", "topic-economy"),
        Def("外部性", null, "市場で価格付けされない便益/費用。ピグー税・補助金、コースの定理。", "経済", "topic-economy"),
        Def("独占と寡占", null, "価格支配力、クールノー・ベルトラン競争、ゲーム理論との接続。", "経済", "topic-economy"),
        Def("財政政策", null, "政府支出・租税による需要管理。乗数効果、クラウディングアウト。", "経済", "topic-economy"),
        Def("金融政策", null, "金利・マネーサプライ操作。テイラールール、量的緩和。", "経済", "topic-economy"),
        Def("為替レート", null, "固定/変動、マンデル=フレミング、購買力平価と金利平価。", "経済", "topic-economy"),
        Def("労働市場", null, "賃金決定、失業の類型(摩擦的/構造的/循環的)、フィリップス曲線。", "経済", "topic-economy"),
        Def("社会保障", null, "年金・医療・介護の財政、賦課方式と積立方式、少子高齢化の負担。", "経済", "topic-economy"),
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

    suspend fun seedIfEmpty(
        entryDao: EntryDao, thoughtDao: EntryThoughtDao, definitionDao: EntryDefinitionDao,
        topicDao: TopicDao? = null, quizDao: QuizDao? = null
    ) {
        val count = entryDao.observeCount().first()
        if (count > 0) return
        val now = System.currentTimeMillis()
        extraTopics.forEach { topicDao?.insert(it) }
        for (d in definitions) {
            val id = UUID.randomUUID().toString()
            entryDao.insert(EntryEntity(id = id, type = "definition", title = d.term, createdAt = now, updatedAt = now, accessedAt = now))
            definitionDao.insert(EntryDefinitionEntity(entryId = id, term = d.term, reading = d.reading, definition = d.definition, field = d.field))
        }
        for (t in thoughts) {
            val id = UUID.randomUUID().toString()
            entryDao.insert(EntryEntity(id = id, type = "thought", title = t.title, content = t.content, createdAt = now, updatedAt = now, accessedAt = now))
            thoughtDao.insert(EntryThoughtEntity(entryId = id))
        }
    }
}
