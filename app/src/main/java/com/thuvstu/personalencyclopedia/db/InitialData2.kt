package com.thuvstu.personalencyclopedia.db

import com.thuvstu.personalencyclopedia.db.dao.*
import com.thuvstu.personalencyclopedia.db.entity.*
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * ★wt45 初期データ第2弾 — 「13型すべてが埋まる知識グラフ」。
 *
 * 第1弾(`InitialData`)は definition/thought の2型だけだった。本弾は
 * 人物・組織・場所・出来事・書籍・Web・動画・文書・メディア・いいね・AI会話を加え、
 * 型付き接続(authored_by / located_at / occurred_at / exemplifies / extends / references)で
 * 既存の定義群と編み合わせる。「明治維新 ─occurred_at→ 京都」「学問のすゝめ ─authored_by→ 福澤諭吉」のように
 * **型固有の接続が実際に意味を持つ**サンプルを作ることが目的。
 *
 * 冪等: 各項目は **タイトル(定義は term)一致で存在確認** してから追加する。空DBガードではないので、
 * 第1弾投入済み・自作エントリー混在のDBに「追記」しても重複しない(Dashboard の文言どおり)。
 * ★wt50: 起動 Phase A がデモ規模DBへ自動で呼ぶ。自作が多いDBは Dashboard 追記ボタンのみ。
 *
 * 端末固有パス(blobPath/coverPath/photoPath)は入れない。document/media は本文テキストだけ持つ
 * (ビューアは「実体なし」を明示する設計・wt43)。
 */
object InitialData2 {

    // ── 型別のデータ定義 ─────────────────────────────────────

    data class Person(val name: String, val reading: String?, val birth: Int?, val death: Int?, val nationality: String,
                      val occupations: List<String>, val bio: String, val topic: String)
    data class Org(val name: String, val orgType: String, val founded: Int?, val country: String, val url: String?, val desc: String, val topic: String)
    data class Place(val name: String, val placeType: String, val address: String?, val lat: Double, val lon: Double, val desc: String, val topic: String)
    data class Event(val name: String, val year: Int, val month: Int, val day: Int, val location: String, val desc: String, val topic: String)
    data class Book(val title: String, val authors: List<String>, val year: Int?, val publisher: String?, val pages: Int?, val note: String, val topic: String)
    data class Web(val title: String, val url: String, val author: String?, val note: String, val topic: String)
    data class Video(val title: String, val platform: String, val channel: String, val durationS: Int, val note: String, val topic: String)
    data class Doc(val title: String, val docType: String, val pages: Int?, val text: String, val topic: String)
    data class Media(val title: String, val mediaType: String, val caption: String, val ocr: String?, val topic: String)
    data class Liked(val title: String, val platform: String, val author: String, val text: String, val topic: String)
    data class AiConv(val title: String, val topic: String, val q: String, val a: String, val q2: String, val a2: String, val topicId: String)
    data class Def(val term: String, val reading: String?, val definition: String, val field: String, val topic: String)
    data class Quiz(val q: String, val a: String, val choices: List<String>, val exp: String, val type: String = "mcq", val hints: List<String> = emptyList())
    /** 接続: (Aタイトル, Bタイトル, 種別, 強度, 注釈)。有向種別は A→B */
    data class Conn(val a: String, val b: String, val type: String, val strength: Float, val note: String)

    val topics = listOf(
        TopicEntity(id = "topic-philosophy", name = "哲学", colorHex = "#0EA5E9"),
        TopicEntity(id = "topic-art", name = "芸術", colorHex = "#D946EF"),
        TopicEntity(id = "topic-learning", name = "学習法", colorHex = "#84CC16"),
    )

    val persons = listOf(
        Person("福澤諭吉", "ふくざわゆきち", 1835, 1901, "日本", listOf("啓蒙思想家", "教育者", "慶應義塾創設者"),
            "中津藩士の子。適塾で蘭学、のち英学へ転じ幕府使節として3度渡航。『西洋事情』『学問のすゝめ』『文明論之概略』で独立自尊と実学を説き、明治日本の精神的基盤を築いた。", "topic-history"),
        Person("紫式部", "むらさきしきぶ", 973, 1014, "日本", listOf("作家", "歌人", "女房"),
            "藤原為時の娘。夫宣孝の死後『源氏物語』を執筆し、一条天皇中宮彰子に出仕。『紫式部日記』で清少納言を評した記述でも知られる。生没年は諸説あり概数。", "topic-koten"),
        Person("松尾芭蕉", "まつおばしょう", 1644, 1694, "日本", listOf("俳諧師"),
            "伊賀上野生まれ。江戸で蕉風を確立し、『野ざらし紀行』『奥の細道』の旅で「不易流行」「軽み」の理念に至る。古池や蛙飛びこむ水の音。", "topic-koten"),
        Person("アダム・スミス", null, 1723, 1790, "イギリス(スコットランド)", listOf("経済学者", "道徳哲学者"),
            "グラスゴー大学教授。『道徳感情論』(1759)で共感の倫理を、『国富論』(1776)で分業と「見えざる手」による市場秩序を論じ、古典派経済学の父とされる。", "topic-economy"),
        Person("アラン・チューリング", null, 1912, 1954, "イギリス", listOf("数学者", "計算機科学者", "暗号解読者"),
            "1936年の論文で計算可能性を定式化した抽象機械(チューリング機械)を提示。第二次大戦中ブレッチリー・パークでエニグマ解読に貢献。1950年「計算機械と知能」でチューリングテストを提案。", "topic-cs"),
        Person("ドナルド・クヌース", null, 1938, null, "アメリカ", listOf("計算機科学者", "スタンフォード大学名誉教授"),
            "『The Art of Computer Programming』の著者。組版システムTeXとフォント設計言語METAFONTを開発。アルゴリズム解析の体系化と「文芸的プログラミング」の提唱者。", "topic-cs"),
        Person("マリー・キュリー", null, 1867, 1934, "ポーランド/フランス", listOf("物理学者", "化学者"),
            "放射能の研究でポロニウムとラジウムを発見。1903年物理学賞・1911年化学賞と、異なる分野で2度ノーベル賞を受けた唯一の人物。", "topic-science"),
        Person("ジョン・メイナード・ケインズ", null, 1883, 1946, "イギリス", listOf("経済学者"),
            "『雇用・利子および貨幣の一般理論』(1936)で有効需要の原理を提示し、不況期の財政政策を正当化。IS-LM分析はヒックスによるその定式化。ブレトンウッズ体制の設計にも関与。", "topic-economy"),
        Person("ソクラテス", null, -470, -399, "古代ギリシア(アテナイ)", listOf("哲学者"),
            "著作を残さず、問答法(産婆術)で対話者の無知を自覚させた。「不敬神と青年堕落」の罪で死刑判決を受け毒杯を仰ぐ。弟子プラトンの対話篇を通じて知られる。", "topic-philosophy"),
        Person("ハンナ・アーレント", null, 1906, 1975, "ドイツ/アメリカ", listOf("政治哲学者"),
            "ナチス政権下で亡命。『全体主義の起原』『人間の条件』、アイヒマン裁判の傍聴記で「悪の陳腐さ」を論じた。活動(action)と公共性の思想家。", "topic-philosophy"),
        Person("葛飾北斎", "かつしかほくさい", 1760, 1849, "日本", listOf("浮世絵師"),
            "『冨嶽三十六景』『北斎漫画』。90年の生涯で30回以上改号し93回転居したと伝わる。印象派やアール・ヌーヴォーに影響(ジャポニスム)。", "topic-art"),
        Person("ヘルマン・エビングハウス", null, 1850, 1909, "ドイツ", listOf("心理学者"),
            "無意味綴りを用いた自己実験で忘却曲線と間隔効果を発見(1885)。記憶研究を実験科学にした。SRS(間隔反復)の理論的源流。", "topic-learning"),
    )

    val orgs = listOf(
        Org("慶應義塾", "私立大学", 1858, "日本", "https://www.keio.ac.jp/", "福澤諭吉が築地鉄砲洲に開いた蘭学塾に始まる。1868年に慶應義塾と命名。「独立自尊」「半学半教」。", "topic-history"),
        Org("日本銀行", "中央銀行", 1882, "日本", "https://www.boj.or.jp/", "日本銀行条例により設立。銀行券発行・金融政策・決済システムの運営を担う。2013年以降の2%物価安定目標と量的・質的緩和が近年の焦点。", "topic-economy"),
        Org("ブレッチリー・パーク", "暗号解読機関(政府暗号学校)", 1939, "イギリス", "https://bletchleypark.org.uk/", "第二次大戦中の英政府暗号学校の所在地。チューリングらがボンブを用いてエニグマを解読。戦後長く秘密にされた。", "topic-cs"),
        Org("Wikipedia(ウィキメディア財団)", "非営利財団", 2001, "アメリカ", "https://www.wikipedia.org/", "誰でも編集できる百科事典。中立的観点・検証可能性・独自研究の排除の三大方針。本アプリの「個人版百科事典」の参照点。", "topic-cs"),
        Org("国際連合", "国際機関", 1945, "国際", "https://www.un.org/", "サンフランシスコ会議で憲章採択、51か国で発足。安保理・総会・ICJ等の主要機関。国際法の法源論(ICJ規程38条)と直結。", "topic-law"),
        Org("アカデメイア", "学園(古代)", -387, "古代ギリシア", null, "プラトンがアテナイ郊外に開いた学園。幾何学を知らざる者入るべからず。アリストテレスも20年学んだ。529年ユスティニアヌス帝により閉鎖。", "topic-philosophy"),
    )

    val places = listOf(
        Place("京都", "都市", "京都府京都市", 35.0116, 135.7681, "794年平安京遷都から1869年の東京奠都まで千年余の都。源氏物語の舞台であり、大政奉還(二条城)の地でもある。", "topic-history"),
        Place("関ヶ原", "古戦場", "岐阜県不破郡関ケ原町", 35.3647, 136.4656, "東山道の要衝。1600年の天下分け目の合戦の地。壬申の乱(672)の激戦地でもある。", "topic-history"),
        Place("平泉", "史跡(世界遺産)", "岩手県西磐井郡平泉町", 38.9868, 141.1140, "奥州藤原氏三代の拠点。中尊寺金色堂。芭蕉が『奥の細道』で「夏草や兵どもが夢の跡」と詠んだ。", "topic-koten"),
        Place("グラスゴー", "都市", "イギリス スコットランド", 55.8642, -4.2518, "産業革命期の造船・商業都市。グラスゴー大学でアダム・スミスが道徳哲学を講じ、ジェームズ・ワットが蒸気機関を改良した。", "topic-economy"),
        Place("ブレッチリー", "町", "イギリス ミルトン・キーンズ", 51.9976, -0.7407, "ロンドン北西70km。ブレッチリー・パークの所在地。現在は博物館。", "topic-cs"),
        Place("アテナイ", "都市(古代)", "ギリシャ アテネ", 37.9838, 23.7275, "古代ギリシアのポリス。民主政・アゴラでの問答・アカデメイアとリュケイオン。西洋哲学と民主主義の発祥地。", "topic-philosophy"),
    )

    val events = listOf(
        Event("大政奉還", 1867, 11, 9, "京都・二条城", "慶応3年10月14日、徳川慶喜が政権を朝廷に返上。王政復古の大号令(12月9日)へ続き、明治維新の直接の起点。", "topic-history"),
        Event("学問のすゝめ 初編刊行", 1872, 2, 1, "東京", "明治5年2月。「天は人の上に人を造らず」で始まる啓蒙書。17編合計340万部と伝えられ、当時の人口の10人に1人が読んだ計算。", "topic-history"),
        Event("国富論 出版", 1776, 3, 9, "ロンドン", "アダム・スミス『諸国民の富の性質と原因の研究』刊行。同年7月にアメリカ独立宣言。分業・市場・自由貿易の古典。", "topic-economy"),
        Event("チューリング「計算可能数について」発表", 1936, 11, 12, "ロンドン数学会", "決定問題に否定的解答を与え、万能機械の概念を提示。現代計算機の理論的出発点。", "topic-cs"),
        Event("一般理論 出版", 1936, 2, 4, "ロンドン", "ケインズ『雇用・利子および貨幣の一般理論』。世界恐慌下で有効需要の不足を不況の原因と論じ、マクロ経済学を創始。", "topic-economy"),
        Event("ソクラテス裁判", -399, 5, 1, "アテナイ", "紀元前399年(表示上の日付は概数)。500人の陪審による裁判で有罪・死刑。『ソクラテスの弁明』『クリトン』『パイドン』の題材。法と良心の対立の原点。", "topic-philosophy"),
        Event("エビングハウス『記憶について』刊行", 1885, 1, 1, "ライプツィヒ", "忘却曲線・節約法・間隔効果を報告。学習1時間後に約56%、1日後に約66%を忘却(節約率換算)というデータが後の間隔反復学習の根拠に。", "topic-learning"),
    )

    val books = listOf(
        Book("学問のすゝめ", listOf("福澤諭吉"), 1872, "岩波文庫(現行)", 256, "一身独立して一国独立す。実学の勧めと、学ぶ者と学ばぬ者の差が貧富・貴賤を生むという主張。初編は小幡篤次郎との共著。", "topic-history"),
        Book("源氏物語（新潮日本古典集成 全8巻）", listOf("紫式部"), 1008, "新潮日本古典集成(現行)", 4800, "全54帖。桐壺〜幻(光源氏)、匂宮〜夢浮橋(宇治十帖)。「もののあはれ」論は本居宣長『源氏物語玉の小櫛』による。", "topic-koten"),
        Book("おくのほそ道（岩波文庫）", listOf("松尾芭蕉"), 1702, "岩波文庫(現行)", 160, "元禄2年(1689)の東北・北陸の旅約2400km、150日。曾良随行。平泉・立石寺・最上川・象潟。刊行は没後。", "topic-koten"),
        Book("国富論", listOf("アダム・スミス"), 1776, "岩波文庫(現行)", 1800, "ピン工場の分業、見えざる手、重商主義批判、自由貿易、租税の四原則。第5編は国家の役割(国防・司法・公共事業・教育)。", "topic-economy"),
        Book("雇用・利子および貨幣の一般理論", listOf("ジョン・メイナード・ケインズ"), 1936, "岩波文庫(現行)", 480, "有効需要・乗数・流動性選好・アニマルスピリッツ。古典派の「供給は自らの需要を生む」を否定。", "topic-economy"),
        Book("The Art of Computer Programming", listOf("ドナルド・クヌース"), 1968, "Addison-Wesley", 3168, "第1巻 基本算法、第2巻 準数値算法、第3巻 ソートと探索、第4巻 組合せ算法。ハッシュ法(第3巻6.4節)の古典的解説。", "topic-cs"),
        Book("人間の条件", listOf("ハンナ・アーレント"), 1958, "ちくま学芸文庫(現行)", 550, "労働(labor)・仕事(work)・活動(action)の三分法。近代における公的領域の喪失を論じる。", "topic-philosophy"),
        Book("ソクラテスの弁明", listOf("プラトン"), -390, "岩波文庫(現行)", 140, "裁判での弁論の再構成。「無知の知」、「吟味されざる生は生きるに値しない」。", "topic-philosophy"),
        Book("使える脳の鍛え方 — 成功する学習の科学", listOf("ピーター・ブラウン", "ヘンリー・ローディガー", "マーク・マクダニエル"), 2014, "NTT出版", 352, "Make It Stick。想起練習・間隔反復・交互学習の証拠を一般向けにまとめた。再読・蛍光ペンは効果が薄い。", "topic-learning"),
    )

    val webs = listOf(
        Web("Wikipedia: 明治維新", "https://ja.wikipedia.org/wiki/明治維新", null, "時代区分・諸改革・研究史の概観。参照点として。", "topic-history"),
        Web("Wikipedia: ハッシュテーブル", "https://ja.wikipedia.org/wiki/ハッシュテーブル", null, "チェイン法・オープンアドレス法・負荷率とリハッシュの図解。", "topic-cs"),
        Web("Spaced repetition (Gwern)", "https://gwern.net/spaced-repetition", "Gwern Branwen", "間隔反復の研究史と実践の長大なレビュー。SM-2 の由来と限界、何をカード化すべきかの議論。", "topic-learning"),
        Web("Stanford Encyclopedia of Philosophy: Socrates", "https://plato.stanford.edu/entries/socrates/", "Debra Nails", "ソクラテス問題(史実のソクラテスをどう復元するか)の学術的整理。", "topic-philosophy"),
        Web("日本銀行: 金融政策の概要", "https://www.boj.or.jp/mopo/outline/index.htm", null, "政策金利・国債買入れ・物価安定目標の一次資料。", "topic-economy"),
        Web("Turing (1936) 原論文 PDF", "https://www.cs.virginia.edu/~robins/Turing_Paper_1936.pdf", "A. M. Turing", "On Computable Numbers, with an Application to the Entscheidungsproblem。", "topic-cs"),
        Web("Learning How to Learn (Coursera)", "https://www.coursera.org/learn/learning-how-to-learn", "Barbara Oakley", "集中モード/拡散モード、チャンク化、先延ばし対策(ポモドーロ)。", "topic-learning"),
    )

    val videos = listOf(
        Video("ハッシュテーブルのしくみ(可視化)", "youtube", "CS Visualized", 720, "配列+ハッシュ関数+衝突処理をアニメーションで。負荷率0.7超でのリハッシュも。", "topic-cs"),
        Video("忘却曲線と間隔反復 — 10分でわかる", "youtube", "学習科学ch", 600, "エビングハウスの実験と現代の再現研究(Murre & Dros 2015)。", "topic-learning"),
        Video("IS-LM分析を図で理解する", "youtube", "経済学入門", 1500, "財政拡大でIS右シフト、金融緩和でLM右シフト。クラウディングアウトの図解。", "topic-economy"),
        Video("源氏物語 — 五十四帖を一気に", "youtube", "古典文学ch", 2700, "光源氏の生涯と宇治十帖の構成。主要な女君の系図。", "topic-koten"),
    )

    val docs = listOf(
        Doc("学習計画テンプレート 2026", "md", null, "# 学習計画\n\n## 週次\n- 月: 数学(微分) 45分\n- 火: 英語(関係詞) 45分\n- 水: 復習(SRS) 30分\n- 木: 古典(助動詞) 45分\n- 金: 経済(IS-LM) 45分\n- 土: クイズ大会 + 白板整理\n- 日: 休み(拡散モード)\n\n## 原則\n1. 想起練習 > 再読\n2. 間隔をあける\n3. 交互に混ぜる", "topic-learning"),
        Doc("SM-2 アルゴリズム メモ", "pdf", 3, "SM-2 (SuperMemo 2, Wozniak 1987)\n\nq: 0-5 の回答品質\nEF' = EF + (0.1 - (5-q)*(0.08 + (5-q)*0.02)), 最小1.3\nq<3 なら反復回数を0に戻し間隔1日\nn=1: 1日, n=2: 6日, n>2: 前回間隔 × EF\n\n本アプリの SrsRepository.recordReview は本方式を採用。", "topic-learning"),
        Doc("民法709条 要件メモ", "docx", 2, "不法行為の成立要件\n1. 故意または過失\n2. 権利または法律上保護される利益の侵害\n3. 損害の発生\n4. 因果関係(相当因果関係説)\n5. 責任能力(712・713条)\n効果: 損害賠償請求権(金銭賠償の原則 722条→417条)。過失相殺(722条2項)。消滅時効: 損害及び加害者を知った時から3年(人身は5年)、不法行為時から20年。", "topic-law"),
    )

    val medias = listOf(
        Media("冨嶽三十六景 神奈川沖浪裏", "image", "葛飾北斎、1831年頃。大波と富士。プルシアンブルーの使用。", null, "topic-art"),
        Media("忘却曲線のグラフ(手描き)", "image", "縦軸: 保持率、横軸: 時間。復習ごとに曲線が緩やかになる様子を重ね描き。", "20分後 58%  1時間後 44%  1日後 34%  6日後 25%  31日後 21%", "topic-learning"),
        Media("関ヶ原 布陣図", "image", "東軍(家康)と西軍(三成)の配置。小早川秀秋の松尾山。", "松尾山 小早川 / 笹尾山 石田 / 桃配山 徳川", "topic-history"),
    )

    val likeds = listOf(
        Liked("「理解した」と「説明できる」の間には海がある", "x", "@learning_notes", "理解したと思ったら、白紙に説明を書いてみる。書けなかった所が本当の穴。ファインマン・テクニック。", "topic-learning"),
        Liked("ハッシュ関数に求められる3性質", "x", "@cs_daily", "①決定的 ②高速 ③均一分布。暗号学的ハッシュはさらに原像計算困難性・衝突困難性。用途で選べ。", "topic-cs"),
        Liked("芭蕉「不易流行」の現代的解釈", "x", "@haiku_bot", "変わらないもの(不易)と変わり続けるもの(流行)は同じ根から出ている。学びも同じで、基礎は不易、応用は流行。", "topic-koten"),
    )

    val aiConvs = listOf(
        AiConv("再帰とハッシュは本当に関係あるのか？", "CS",
            "接続の注釈に『再帰の基底ケース理解がハッシュの衝突処理に通じる』とあるが、正直こじつけでは？",
            "半分正しい指摘です。直接の依存関係はありません。ただし共通する思考パターンはあります: どちらも「終了条件を先に決める」設計です。再帰は基底ケース、オープンアドレス法は空きスロットか探索上限で止まる。チェイン法の連結リスト走査も再帰的に書けます。関係の種類としては prerequisite(前提) より related(関連) が適切でしょう。",
            "では接続の種別を related に変えた方がよい？",
            "はい。prerequisite は「Aを知らないとBが理解できない」場合に限る方が、後で学習順序を自動生成する時に破綻しません。",
            "topic-cs"),
        AiConv("間隔反復の間隔はどう決まる？", "学習法",
            "SM-2 の EF(易しさ係数)が 1.3 未満にならないのはなぜ？",
            "EF が小さいほど間隔の伸びが鈍くなります。1.3 未満を許すと間隔がほぼ伸びず、同じカードが毎日出続けて学習者が疲弊するため、Wozniak が経験的に下限を置きました。FSRS などの後続手法は EF の代わりに記憶の安定性と難易度を分けてモデル化しています。",
            "本アプリではどちらを使っている？",
            "SrsRepository は SM-2 系です。1回目1日、2回目6日、以降は前回間隔×EF。グレード3未満で反復回数がリセットされます。",
            "topic-learning"),
    )

    /** 追加の定義: 上の人物・出来事と接続できる概念を補う */
    val definitions = listOf(
        Def("独立自尊", "どくりつじそん", "【定義】福澤諭吉の思想の核。他に依存せず自らの判断で行動し、人格の尊厳を保つこと。【体系】学問のすゝめ→文明論之概略→慶應義塾の校是。【例】「一身独立して一国独立す」。", "思想", "topic-history"),
        Def("見えざる手", null, "【定義】各人が自己利益を追求すると、意図せず社会全体の利益が促進されるという市場の調整作用の比喩。【体系】国富論第4編。分業・価格メカニズムと一体。【例】パン屋は慈悲ではなく利益のためにパンを焼くが、結果として社会にパンが行き渡る。", "経済", "topic-economy"),
        Def("有効需要", "ゆうこうじゅよう", "【定義】貨幣支出に裏付けられた需要。ケインズは総需要(消費+投資+政府支出+純輸出)が雇用水準を決めると論じた。【体系】一般理論→IS-LM→財政政策。【例】不況期の公共投資は乗数効果で所得を増やす。", "経済", "topic-economy"),
        Def("チューリング機械", null, "【定義】無限テープ・ヘッド・有限状態制御からなる抽象的計算モデル。【体系】計算可能性→万能機械→停止問題の決定不能性→計算量理論。【例】どんなアルゴリズムもチューリング機械で模倣できる(チャーチ=チューリングのテーゼ)。", "CS", "topic-cs"),
        Def("停止問題", "ていしもんだい", "【定義】任意のプログラムと入力に対し、それが停止するか否かを判定する一般的アルゴリズムは存在しない(1936年チューリング)。【体系】対角線論法による証明。ゲーデルの不完全性定理と同型。【例】完全なバグ検出器は原理的に作れない。", "CS", "topic-cs"),
        Def("忘却曲線", "ぼうきゃくきょくせん", "【定義】記憶の保持率が時間とともに指数関数的に低下する様子を表す曲線。エビングハウス(1885)。【体系】忘却曲線→間隔効果→間隔反復(SRS)。【例】復習のたびに曲線は緩やかになり、次の復習までの間隔を伸ばせる。", "学習法", "topic-learning"),
        Def("間隔反復", "かんかくはんぷく", "【定義】忘れかけた頃に復習する間隔を徐々に広げる学習法。【体系】エビングハウス→ライトナーの箱→SM-2(1987)→FSRS。【例】1日→6日→2週→1か月。本アプリのSRSレビューがこれ。", "学習法", "topic-learning"),
        Def("想起練習", "そうきれんしゅう", "【定義】情報を見直すのではなく、思い出そうとする行為自体が記憶を強化するという原理(テスト効果)。【体系】Roediger & Karpicke(2006)。再読の3倍以上の長期保持。【例】教科書を閉じて白紙に書き出す。クイズで解く。", "学習法", "topic-learning"),
        Def("無知の知", "むちのち", "【定義】自分が知らないということを自覚していること。ソクラテスがデルフォイの神託を吟味して至った態度。【体系】問答法→イデア論(プラトン)→懐疑主義。【例】「私は知らないことを知っている点で、知っていると思い込む人より賢い」。", "哲学", "topic-philosophy"),
        Def("問答法", "もんどうほう", "【定義】質問を重ねて相手の答えの矛盾を明らかにし、より正確な理解へ導く方法。産婆術・エレンコス。【体系】ソクラテス→プラトンの対話篇→弁証法。【例】「勇気とは何か」に対する定義を次々と吟味する『ラケス』。", "哲学", "topic-philosophy"),
        Def("悪の陳腐さ", "あくのちんぷさ", "【定義】アーレントがアイヒマン裁判で見出した概念。巨大な悪は狂気ではなく、思考停止した凡庸な官僚性から生まれるという洞察。【体系】全体主義の起原→人間の条件→精神の生活。【例】命令に従っただけ、という弁明。", "哲学", "topic-philosophy"),
        Def("浮世絵", "うきよえ", "【定義】江戸時代に成立した木版画・肉筆画。都市の風俗・役者・美人・風景を描く。【体系】菱川師宣→鈴木春信(錦絵)→歌麿・写楽→北斎・広重。【例】神奈川沖浪裏。19世紀欧州のジャポニスムに影響。", "芸術", "topic-art"),
        Def("ジャポニスム", null, "【定義】19世紀後半の欧州における日本美術の流行と影響。【体系】1867年パリ万博→印象派・ゴッホ・アール・ヌーヴォー。【例】モネ『ラ・ジャポネーズ』、ゴッホの広重模写。", "芸術", "topic-art"),
        Def("不易流行", "ふえきりゅうこう", "【定義】芭蕉の俳諧理念。永遠に変わらぬ本質(不易)と、時代に応じて変化する新しさ(流行)は、風雅の誠において一つであるという考え。【体系】蕉風→去来抄・三冊子。【例】古池の句は不易、新しい季語や題材は流行。", "古典", "topic-koten"),
        Def("暗号解読(エニグマ)", null, "【定義】独軍の回転式暗号機エニグマの解読。ポーランドのレイェフスキが数学的基礎を築き、英ブレッチリー・パークでチューリングらが電気機械ボンブで実用化。【体系】暗号史→計算機の誕生。【例】既知平文(天気予報の定型文)を手がかりにした。", "CS", "topic-cs"),
    )

    val thoughts = listOf(
        DemoData.DemoThought("人物から辿ると歴史が線になる", "福澤諭吉→慶應義塾→学問のすゝめ→独立自尊。人物をハブにすると出来事と概念が自然に繋がる。定義だけ並べても点のまま。"),
        DemoData.DemoThought("接続の種類を使い分ける", "authored_by / located_at / occurred_at は事実。related は感想。両者を混ぜると検索結果が濁る。事実の接続を先に張り、related は後から間引く。"),
        DemoData.DemoThought("13型の使い分けメモ", "Webは「後で読む」、documentは「自分で書いた/もらった」、mediaは「見て覚える」、likedは「他人の一言」、ai_convは「疑問と回答」。型を迷ったら、あとで何をしたいかで決める。"),
    )

    val quizzes = listOf(
        Quiz("『学問のすゝめ』の著者は？", "福澤諭吉", listOf("福澤諭吉", "中江兆民", "新渡戸稲造", "夏目漱石"), "明治5年初編。慶應義塾の創設者。"),
        Quiz("大政奉還が行われた場所は？", "二条城", listOf("二条城", "江戸城", "大坂城", "御所"), "京都・二条城二の丸御殿。1867年。"),
        Quiz("『国富論』の出版年は？", "1776年", listOf("1776年", "1759年", "1789年", "1848年"), "同年アメリカ独立宣言。『道徳感情論』は1759年。"),
        Quiz("チューリングが1936年の論文で扱った問題は？", "決定問題", listOf("決定問題", "四色問題", "P対NP問題", "リーマン予想"), "Entscheidungsproblem。計算可能性の定式化。"),
        Quiz("『一般理論』でケインズが不況の原因としたものは？", "有効需要の不足", listOf("有効需要の不足", "貨幣供給の過剰", "技術進歩の停滞", "人口減少"), "総需要が雇用を決める。"),
        Quiz("エビングハウスが忘却曲線を発表した年は？", "1885年", listOf("1885年", "1859年", "1905年", "1936年"), "『記憶について』。無意味綴りの自己実験。"),
        Quiz("ソクラテスの死刑判決の罪状に含まれるのは？", "青年を堕落させた", listOf("青年を堕落させた", "国庫を横領した", "敵国に通じた", "神殿を破壊した"), "不敬神と青年堕落。『ソクラテスの弁明』。"),
        Quiz("『冨嶽三十六景』の作者は？", "葛飾北斎", listOf("葛飾北斎", "歌川広重", "喜多川歌麿", "東洲斎写楽"), "北斎70代の作。広重は『東海道五十三次』。"),
        Quiz("芭蕉が「夏草や兵どもが夢の跡」を詠んだ地は？", "平泉", listOf("平泉", "松島", "立石寺", "象潟"), "奥州藤原氏の旧跡。『奥の細道』。"),
        Quiz("SM-2 で易しさ係数(EF)の下限は？", "1.3", listOf("1.3", "1.0", "2.5", "0.5"), "これ未満だと間隔がほぼ伸びず疲弊するため。"),
        Quiz("アーレントがアイヒマン裁判から導いた概念は？", "悪の陳腐さ", listOf("悪の陳腐さ", "永劫回帰", "ルサンチマン", "パノプティコン"), "思考停止した凡庸さが巨大な悪を生む。"),
        Quiz("『人間の条件』の三分法は 労働・仕事・（　）", "活動", listOf("活動", "遊戯", "祈り", "闘争"), "action。公的領域での言論と行為。", "fill_blank", listOf("英語では action", "公的領域と関係")),
        Quiz("見えざる手を論じた書物は『（　）』", "国富論", listOf("国富論", "道徳感情論", "資本論", "一般理論"), "第4編第2章。", "fill_blank", listOf("1776年刊")),
        Quiz("停止問題が決定不能であることの証明法は（　）論法", "対角線", listOf("対角線", "背理", "数学的帰納", "鳩の巣"), "カントールの対角線論法の応用。", "fill_blank"),
        Quiz("エニグマ解読の拠点はブレッチリー・（　）", "パーク", listOf("パーク", "ハウス", "ホール", "タワー"), "英政府暗号学校。", "fill_blank"),
        Quiz("ソクラテスの問答法は別名（　）術", "産婆", listOf("産婆", "弁論", "説得", "詭弁"), "相手の中にある考えを引き出す。", "fill_blank"),
    )

    val wikis = listOf(
        WikiArticleEntity(
            title = "福澤諭吉と明治の啓蒙", summary = "人物→著作→組織→思想を1枚に",
            contentMd = "# 福澤諭吉と明治の啓蒙\n\n[[福澤諭吉]] は [[慶應義塾]] を開き、[[学問のすゝめ]] で [[独立自尊]] を説いた。\n\n- 背景: [[大政奉還]] → [[明治維新]] → [[地租改正]]\n- 出来事: [[学問のすゝめ 初編刊行]] (1872)\n- 場所: [[京都]] (旧都) から東京へ\n\n> 「一身独立して一国独立す」— 個人の自立が国家の独立の前提。\n"
        ),
        WikiArticleEntity(
            title = "計算の起源 — チューリングから現代へ", summary = "人物・論文・機関・概念の接続",
            contentMd = "# 計算の起源\n\n[[アラン・チューリング]] の [[チューリング「計算可能数について」発表]] (1936) が [[チューリング機械]] と [[停止問題]] を生んだ。\n\n- 実践: [[暗号解読(エニグマ)]] @ [[ブレッチリー・パーク]]\n- 体系化: [[ドナルド・クヌース]] の [[The Art of Computer Programming]] → [[ハッシュテーブル]] / [[再帰]]\n- 疑問: [[再帰とハッシュは本当に関係あるのか？]] (AI会話)\n"
        ),
        WikiArticleEntity(
            title = "学習科学ハブ — なぜこのアプリはSRSとクイズを持つのか", summary = "忘却曲線→間隔反復→想起練習",
            contentMd = "# 学習科学ハブ\n\n1. [[ヘルマン・エビングハウス]] が [[忘却曲線]] を発見 ([[エビングハウス『記憶について』刊行]])\n2. [[間隔反復]] が忘却に先回りする — 実装は [[SM-2 アルゴリズム メモ]]\n3. [[想起練習]] が再読に勝る — 参考 [[使える脳の鍛え方 — 成功する学習の科学]]\n\n- 動画: [[忘却曲線と間隔反復 — 10分でわかる]]\n- 計画: [[学習計画テンプレート 2026]]\n- 一言: [[「理解した」と「説明できる」の間には海がある]]\n"
        ),
        WikiArticleEntity(
            title = "経済思想の系譜 — スミスからケインズへ", summary = "見えざる手 vs 有効需要",
            contentMd = "# 経済思想の系譜\n\n| | [[アダム・スミス]] | [[ジョン・メイナード・ケインズ]] |\n|---|---|---|\n| 著作 | [[国富論]] (1776) | [[雇用・利子および貨幣の一般理論]] (1936) |\n| 核心 | [[見えざる手]] | [[有効需要]] |\n| 政策 | 自由放任 | [[財政政策]] |\n\n現代の接点: [[IS-LM分析]] / [[金融政策]] / [[日本銀行]]\n"
        ),
        WikiArticleEntity(
            title = "アテナイの哲学 — ソクラテスからアーレントまで", summary = "問答・無知の知・活動",
            contentMd = "# アテナイの哲学\n\n[[ソクラテス]] は [[アテナイ]] で [[問答法]] を実践し [[無知の知]] に至った。[[ソクラテス裁判]] の弁論は [[ソクラテスの弁明]] に残る。\n\nプラトンの [[アカデメイア]] を経て、20世紀の [[ハンナ・アーレント]] は [[人間の条件]] で「活動」を、アイヒマン論で [[悪の陳腐さ]] を論じた。\n"
        ),
    )

    val connections = listOf(
        // authored_by (A=著作 → B=著者)
        Conn("学問のすゝめ", "福澤諭吉", "authored_by", 1.0f, "初編は小幡篤次郎と共著"),
        Conn("源氏物語（新潮日本古典集成 全8巻）", "紫式部", "authored_by", 1.0f, ""),
        Conn("源氏物語", "紫式部", "related", 1.0f, "作者(定義→人物)"),
        Conn("おくのほそ道（岩波文庫）", "松尾芭蕉", "authored_by", 1.0f, ""),
        Conn("奥の細道", "松尾芭蕉", "related", 1.0f, "作者(定義→人物)"),
        Conn("国富論", "アダム・スミス", "authored_by", 1.0f, ""),
        Conn("雇用・利子および貨幣の一般理論", "ジョン・メイナード・ケインズ", "authored_by", 1.0f, ""),
        Conn("The Art of Computer Programming", "ドナルド・クヌース", "authored_by", 1.0f, ""),
        Conn("人間の条件", "ハンナ・アーレント", "authored_by", 1.0f, ""),
        Conn("冨嶽三十六景 神奈川沖浪裏", "葛飾北斎", "authored_by", 1.0f, ""),
        Conn("Turing (1936) 原論文 PDF", "アラン・チューリング", "authored_by", 1.0f, ""),
        // located_at (A=組織 → B=場所)
        Conn("ブレッチリー・パーク", "ブレッチリー", "located_at", 1.0f, ""),
        Conn("アカデメイア", "アテナイ", "located_at", 1.0f, "郊外の英雄アカデモスの杜"),
        // occurred_at (A=出来事 → B=場所)
        Conn("大政奉還", "京都", "occurred_at", 1.0f, "二条城"),
        Conn("関ヶ原の戦い", "関ヶ原", "occurred_at", 1.0f, ""),
        Conn("ソクラテス裁判", "アテナイ", "occurred_at", 1.0f, ""),
        // exemplifies (A=具体 → B=概念)
        Conn("学問のすゝめ", "独立自尊", "exemplifies", 0.9f, ""),
        Conn("国富論", "見えざる手", "exemplifies", 0.9f, ""),
        Conn("雇用・利子および貨幣の一般理論", "有効需要", "exemplifies", 0.9f, ""),
        Conn("冨嶽三十六景 神奈川沖浪裏", "浮世絵", "exemplifies", 0.9f, ""),
        Conn("SM-2 アルゴリズム メモ", "間隔反復", "exemplifies", 0.9f, ""),
        Conn("ソクラテスの弁明", "無知の知", "exemplifies", 0.9f, ""),
        Conn("暗号解読(エニグマ)", "チューリング機械", "exemplifies", 0.6f, "理論と実践"),
        // extends (A=発展 → B=元)
        Conn("間隔反復", "忘却曲線", "extends", 0.9f, "忘却に先回りする"),
        Conn("有効需要", "需要と供給", "extends", 0.7f, "マクロへの拡張"),
        Conn("停止問題", "チューリング機械", "extends", 0.9f, ""),
        Conn("ジャポニスム", "浮世絵", "extends", 0.8f, "欧州への波及"),
        Conn("IS-LM分析", "有効需要", "extends", 0.8f, "ヒックスによる定式化"),
        // references (A=参照元 → B=参照先)
        Conn("再帰とハッシュは本当に関係あるのか？", "ハッシュテーブル", "references", 0.7f, ""),
        Conn("再帰とハッシュは本当に関係あるのか？", "再帰", "references", 0.7f, ""),
        Conn("間隔反復の間隔はどう決まる？", "間隔反復", "references", 0.8f, ""),
        Conn("忘却曲線のグラフ(手描き)", "忘却曲線", "references", 0.8f, ""),
        Conn("関ヶ原 布陣図", "関ヶ原の戦い", "references", 0.8f, ""),
        Conn("民法709条 要件メモ", "不法行為", "references", 0.9f, ""),
        Conn("Wikipedia: ハッシュテーブル", "ハッシュテーブル", "references", 0.6f, ""),
        Conn("Wikipedia: 明治維新", "明治維新", "references", 0.6f, ""),
        Conn("Spaced repetition (Gwern)", "間隔反復", "references", 0.7f, ""),
        Conn("ハッシュテーブルのしくみ(可視化)", "ハッシュテーブル", "references", 0.6f, ""),
        Conn("IS-LM分析を図で理解する", "IS-LM分析", "references", 0.6f, ""),
        Conn("源氏物語 — 五十四帖を一気に", "源氏物語", "references", 0.6f, ""),
        Conn("ハッシュ関数に求められる3性質", "ハッシュテーブル", "references", 0.5f, ""),
        Conn("芭蕉「不易流行」の現代的解釈", "不易流行", "references", 0.6f, ""),
        Conn("「理解した」と「説明できる」の間には海がある", "想起練習", "references", 0.6f, ""),
        // related (無向)
        Conn("福澤諭吉", "慶應義塾", "related", 0.9f, "創設者"),
        Conn("福澤諭吉", "独立自尊", "related", 1.0f, "思想の核"),
        Conn("学問のすゝめ 初編刊行", "福澤諭吉", "related", 0.9f, ""),
        Conn("アラン・チューリング", "チューリング機械", "related", 1.0f, "提唱者"),
        Conn("アラン・チューリング", "停止問題", "related", 0.9f, ""),
        Conn("アラン・チューリング", "暗号解読(エニグマ)", "related", 0.9f, ""),
        Conn("ソクラテス", "アテナイ", "related", 0.9f, "生涯を過ごした"),
        Conn("ソクラテスの弁明", "ソクラテス", "references", 1.0f, "弁論の記録"),
        Conn("福澤諭吉", "明治維新", "related", 0.7f, "同時代の啓蒙"),
        Conn("大政奉還", "明治維新", "related", 0.9f, "起点"),
        Conn("アラン・チューリング", "ブレッチリー・パーク", "related", 0.9f, "所属"),
        Conn("アダム・スミス", "グラスゴー", "related", 0.8f, "教授職"),
        Conn("ジョン・メイナード・ケインズ", "財政政策", "related", 0.8f, ""),
        Conn("ヘルマン・エビングハウス", "忘却曲線", "related", 1.0f, "発見者"),
        Conn("松尾芭蕉", "平泉", "related", 0.8f, "奥の細道の旅"),
        Conn("松尾芭蕉", "不易流行", "related", 0.9f, ""),
        Conn("ソクラテス", "問答法", "related", 1.0f, ""),
        Conn("ソクラテス", "無知の知", "related", 1.0f, ""),
        Conn("ハンナ・アーレント", "悪の陳腐さ", "related", 1.0f, ""),
        Conn("葛飾北斎", "浮世絵", "related", 1.0f, ""),
        Conn("想起練習", "間隔反復", "related", 0.8f, "両輪"),
        Conn("紫式部", "京都", "related", 0.7f, "宮廷"),
        Conn("学習計画テンプレート 2026", "間隔反復", "related", 0.6f, ""),
        Conn("13型の使い分けメモ", "接続の種類を使い分ける", "related", 0.7f, ""),
    )

    /** タグ: エントリータイトル → タグ名 */
    val tags: Map<String, List<String>> = mapOf(
        "福澤諭吉" to listOf("明治", "人物", "教育"),
        "学問のすゝめ" to listOf("明治", "古典的名著"),
        "慶應義塾" to listOf("明治", "教育"),
        "アラン・チューリング" to listOf("人物", "計算理論", "暗号"),
        "チューリング機械" to listOf("計算理論"),
        "停止問題" to listOf("計算理論"),
        "暗号解読(エニグマ)" to listOf("暗号", "第二次大戦"),
        "ブレッチリー・パーク" to listOf("暗号", "第二次大戦"),
        "忘却曲線" to listOf("記憶", "SRS"),
        "間隔反復" to listOf("記憶", "SRS"),
        "想起練習" to listOf("記憶"),
        "SM-2 アルゴリズム メモ" to listOf("SRS", "実装"),
        "ヘルマン・エビングハウス" to listOf("人物", "記憶"),
        "アダム・スミス" to listOf("人物", "古典派"),
        "国富論" to listOf("古典的名著", "古典派"),
        "ジョン・メイナード・ケインズ" to listOf("人物", "ケインズ派"),
        "雇用・利子および貨幣の一般理論" to listOf("古典的名著", "ケインズ派"),
        "ソクラテス" to listOf("人物", "古代ギリシア"),
        "ハンナ・アーレント" to listOf("人物", "20世紀"),
        "葛飾北斎" to listOf("人物", "江戸"),
        "松尾芭蕉" to listOf("人物", "江戸"),
        "紫式部" to listOf("人物", "平安"),
    )

    // ── 投入 ─────────────────────────────────────────────────

    data class Result(val added: Int, val skipped: Int, val connections: Int)

    /**
     * 冪等追記。存在確認はタイトル一致(`EntryDao.findByTitle`)。
     * 既存タイトル(第1弾やユーザー作成)は再利用して接続だけ張る。
     */
    suspend fun seedAppend(
        entryDao: EntryDao,
        thoughtDao: EntryThoughtDao,
        definitionDao: EntryDefinitionDao,
        extensionDao: EntryExtensionDao,
        tagDao: TagDao,
        topicDao: TopicDao? = null,
        quizDao: QuizDao? = null,
        connectionDao: ConnectionDao? = null,
        whiteboardDao: WhiteboardDao? = null,
        wikiDao: WikiArticleDao? = null
    ): Result {
        val now = System.currentTimeMillis()
        var added = 0
        var skipped = 0
        val idMap = mutableMapOf<String, String>()   // title → entryId

        (DemoData.topics + InitialData.extraTopics + topics).forEach { topicDao?.insert(it) }

        /** 既存ならそのIDを返し、無ければ entry を作って block で拡張を入れる */
        suspend fun ensure(title: String, type: String, topicId: String?, content: String? = null,
                           sourceUrl: String? = null, insertExt: suspend (String) -> Unit): String {
            entryDao.findByTitle(title)?.let { existing ->
                idMap[title] = existing.id; skipped++; return existing.id
            }
            val id = UUID.randomUUID().toString()
            entryDao.insert(EntryEntity(id = id, type = type, title = title, content = content, sourceUrl = sourceUrl,
                createdAt = now, updatedAt = now, accessedAt = now))
            insertExt(id)
            if (topicId != null) {
                try { topicDao?.linkEntryTopic(EntryTopicEntity(entryId = id, topicId = topicId)) } catch (_: Exception) {}
            }
            idMap[title] = id; added++
            return id
        }
        fun jsonArr(items: List<String>) = "[" + items.joinToString(",") { "\"" + it.replace("\"", "\\\"") + "\"" } + "]"
        fun epoch(year: Int, month: Int, day: Int): Long =
            java.util.GregorianCalendar(year, month - 1, day).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.timeInMillis

        for (p in persons) ensure(p.name, "person", p.topic, content = p.bio) { id ->
            extensionDao.insertPerson(EntryPersonEntity(entryId = id, fullName = p.name, aliasesJson = jsonArr(listOfNotNull(p.reading)),
                birthYear = p.birth, deathYear = p.death, nationality = p.nationality, occupationsJson = jsonArr(p.occupations), biography = p.bio))
        }
        for (o in orgs) ensure(o.name, "org", o.topic, content = o.desc, sourceUrl = o.url) { id ->
            extensionDao.insertOrg(EntryOrgEntity(entryId = id, officialName = o.name, orgType = o.orgType, foundedYear = o.founded,
                country = o.country, websiteUrl = o.url, description = o.desc))
        }
        for (pl in places) ensure(pl.name, "place", pl.topic, content = pl.desc) { id ->
            extensionDao.insertPlace(EntryPlaceEntity(entryId = id, placeName = pl.name, placeType = pl.placeType, address = pl.address,
                latitude = pl.lat, longitude = pl.lon))
        }
        for (ev in events) ensure(ev.name, "event", ev.topic, content = ev.desc) { id ->
            extensionDao.insertEvent(EntryEventEntity(entryId = id, eventName = ev.name, startedAt = epoch(ev.year, ev.month, ev.day),
                locationText = ev.location, isPersonal = false))
        }
        for (b in books) ensure(b.title, "book", b.topic, content = b.note) { id ->
            extensionDao.insertBook(EntryBookEntity(entryId = id, authorsJson = jsonArr(b.authors), publisher = b.publisher,
                publishedYear = b.year, totalPages = b.pages, readStatus = "unread"))
        }
        for (w in webs) ensure(w.title, "webpage", w.topic, content = w.note, sourceUrl = w.url) { id ->
            val domain = w.url.removePrefix("https://").removePrefix("http://").substringBefore('/')
            extensionDao.insertWebpage(EntryWebpageEntity(entryId = id, url = w.url, domain = domain, author = w.author, scraperUsed = "seed"))
        }
        for (v in videos) ensure(v.title, "video", v.topic, content = v.note) { id ->
            extensionDao.insertVideo(EntryVideoEntity(entryId = id, platform = v.platform, channelName = v.channel, durationS = v.durationS))
        }
        for (d in docs) ensure(d.title, "document", d.topic, content = d.text.take(500)) { id ->
            val mime = when (d.docType) { "pdf" -> "application/pdf"; "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; else -> "text/markdown" }
            extensionDao.insertDocument(EntryDocumentEntity(entryId = id, docType = d.docType, mimeType = mime, pageCount = d.pages,
                extractedText = d.text, extractionMethod = "seed"))
        }
        for (m in medias) ensure(m.title, "media", m.topic, content = m.caption) { id ->
            extensionDao.insertMedia(EntryMediaEntity(entryId = id, mediaType = m.mediaType, blobPath = "", mimeType = "image/png",
                caption = m.caption, ocrText = m.ocr))
        }
        for (l in likeds) ensure(l.title, "liked", l.topic, content = l.text) { id ->
            extensionDao.insertLiked(EntryLikedEntity(entryId = id, platform = l.platform, originalId = "seed-" + id.take(8),
                likedAt = now, contentType = "post", authorName = l.author, fullText = l.text))
        }
        for (c in aiConvs) ensure(c.title, "ai_conv", c.topicId, content = c.a.take(300)) { id ->
            fun msg(role: String, text: String) = "{\"role\":\"$role\",\"content\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"}"
            extensionDao.insertAiConv(EntryAiConvEntity(entryId = id, model = "gemini-2.5-flash", provider = "google",
                messagesJson = "[" + listOf(msg("user", c.q), msg("assistant", c.a), msg("user", c.q2), msg("assistant", c.a2)).joinToString(",") + "]",
                topic = c.topic, isUseful = true))
        }
        for (d in definitions) ensure(d.term, "definition", d.topic) { id ->
            definitionDao.insert(EntryDefinitionEntity(entryId = id, term = d.term, reading = d.reading, definition = d.definition, field = d.field))
        }
        for (t in thoughts) ensure(t.title, "thought", "topic-learning", content = t.content) { id ->
            thoughtDao.insert(EntryThoughtEntity(entryId = id))
        }

        // タグ(名前で再利用)
        for ((title, names) in tags) {
            val eid = idMap[title] ?: continue
            for (name in names) {
                val tag = tagDao.getByName(name) ?: run { val t = TagEntity(name = name); tagDao.insert(t); tagDao.getByName(name) ?: t }
                try { tagDao.linkTag(EntryTagEntity(entryId = eid, tagId = tag.id)) } catch (_: Exception) {}
            }
        }

        // クイズ(設問文で冪等)
        if (quizDao != null) for (q in quizzes) {
            if (quizDao.countByQuestion(q.q) > 0) continue
            quizDao.insertQuiz(QuizBankEntity(question = q.q, answer = q.a, choicesJson = jsonArr(q.choices), hintsJson = jsonArr(q.hints),
                explanation = q.exp, quizType = q.type, generationMethod = "initial"))
        }

        // Wiki(タイトルで冪等)
        if (wikiDao != null) for (w in wikis) {
            if (wikiDao.findByTitle(w.title) == null) wikiDao.upsert(w)
        }

        // 接続: 第1弾・自作の既存タイトルにも張れるよう、idMap に無いものは DB を引く
        var conns = 0
        if (connectionDao != null) {
            suspend fun resolve(title: String): String? = idMap[title] ?: entryDao.findByTitle(title)?.id?.also { idMap[title] = it }
            for (c in connections) {
                val a = resolve(c.a) ?: continue
                val b = resolve(c.b) ?: continue
                if (a == b) continue
                val typeDef = connectionDao.getTypeDef(c.type)
                val directed = typeDef?.isDirected ?: (c.type != "related")
                val ca = if (a < b) a else b
                val cb = if (a < b) b else a
                val dup = if (directed) connectionDao.countDirectedDuplicate(a, b, c.type) > 0
                          else connectionDao.countUndirectedDuplicate(ca, cb, c.type) > 0
                if (dup) continue
                try {
                    val r = connectionDao.insert(ConnectionEntity(entryAId = a, entryBId = b, relationType = c.type, strength = c.strength,
                        note = c.note.ifBlank { null }, isAuto = false, isDirected = directed, canonicalA = ca, canonicalB = cb))
                    if (r > 0) conns++
                } catch (_: Exception) {}
            }
        }

        // 白板: 「人物ハブ」ボード(同名ボードが無ければ)
        val boardTitle = "人物ハブ — 福澤・チューリング・ソクラテス"
        if (whiteboardDao != null && whiteboardDao.observeBoards().first().none { it.title == boardTitle }) {
            val boardId = UUID.randomUUID().toString()
            whiteboardDao.upsertBoard(WhiteboardEntity(id = boardId, title = boardTitle,
                summary = "人物を中心に著作・組織・出来事・概念を放射状に配置。接続線は型付き接続と対応", createdAt = now, updatedAt = now))
            val hubs = listOf(
                Triple("福澤諭吉", listOf("学問のすゝめ", "慶應義塾", "独立自尊", "学問のすゝめ 初編刊行"), "#FEF3C7"),
                Triple("アラン・チューリング", listOf("チューリング機械", "停止問題", "ブレッチリー・パーク", "暗号解読(エニグマ)"), "#DBEAFE"),
                Triple("ソクラテス", listOf("問答法", "無知の知", "アテナイ", "ソクラテスの弁明"), "#E0F2FE"),
            )
            for ((idx, hub) in hubs.withIndex()) {
                val (center, spokes, color) = hub
                val cx = 60f + idx * 720f
                val sectionId = UUID.randomUUID().toString()
                whiteboardDao.upsertSection(WhiteboardSectionEntity(id = sectionId, boardId = boardId, title = center, x = cx - 20f, y = 20f, width = 680f, height = 520f, colorHex = color))
                val centerEid = idMap[center] ?: continue
                val centerNode = WhiteboardNodeEntity(boardId = boardId, entryId = centerEid, x = cx + 220f, y = 200f, width = 220f, height = 120f, sectionId = sectionId, zIndex = 1)
                whiteboardDao.upsertNode(centerNode)
                val positions = listOf(cx to 40f, cx + 440f to 40f, cx to 380f, cx + 440f to 380f)
                for ((sIdx, spoke) in spokes.withIndex()) {
                    val eid = idMap[spoke] ?: continue
                    val (x, y) = positions[sIdx]
                    val node = WhiteboardNodeEntity(boardId = boardId, entryId = eid, x = x, y = y, width = 220f, height = 110f, sectionId = sectionId)
                    whiteboardDao.upsertNode(node)
                    val relType = connections.firstOrNull { (it.a == spoke && it.b == center) || (it.a == center && it.b == spoke) }?.type
                    val label = relType?.let { t -> connectionDao?.getTypeDef(t)?.labelJa ?: t }
                    whiteboardDao.upsertEdge(WhiteboardEdgeEntity(boardId = boardId, sourceNodeId = centerNode.id, targetNodeId = node.id,
                        label = label, createdAt = now))
                }
            }
        }

        return Result(added = added, skipped = skipped, connections = conns)
    }
}
