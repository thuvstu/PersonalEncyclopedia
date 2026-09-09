package com.thuvstu.personalencyclopedia.db

import com.thuvstu.personalencyclopedia.db.InitialDataHighSchool.Conn
import com.thuvstu.personalencyclopedia.db.InitialDataHighSchool.Def
import com.thuvstu.personalencyclopedia.db.InitialDataHighSchool.Quiz
import com.thuvstu.personalencyclopedia.db.InitialDataHighSchool.SubjectSeed
import com.thuvstu.personalencyclopedia.db.entity.TopicEntity
import com.thuvstu.personalencyclopedia.db.entity.WikiArticleEntity

/** ★wt57: 高校英語（文法詳細・構文・語法・読解・英作文・音声）。既存 topic-english の概要20件はハブとして残す。 */
object HsEnglish {
    private const val T = "topic-english"
    private const val TG = "topic-english-grammar"
    private const val TS = "topic-english-syntax"
    private const val TU = "topic-english-usage"
    private const val TR = "topic-english-reading"
    private const val TW = "topic-english-writing"

    private fun g(t: String, d: String) = Def(t, null, d, "英文法", TG)
    private fun s(t: String, d: String) = Def(t, null, d, "英語構文", TS)
    private fun u(t: String, d: String) = Def(t, null, d, "語法・語彙", TU)
    private fun r(t: String, d: String) = Def(t, null, d, "読解", TR)
    private fun w(t: String, d: String) = Def(t, null, d, "英作文・音声", TW)

    val seed = SubjectSeed(
        key = "english", name = "英語", rootTopicId = T,
        topics = listOf(
            TopicEntity(id = T, name = "英語", colorHex = "#F97316"),
            TopicEntity(id = TG, name = "英文法", parentId = T, colorHex = "#EA580C", description = "時制・助動詞・準動詞・仮定法・関係詞・比較"),
            TopicEntity(id = TS, name = "英語構文", parentId = T, colorHex = "#C2410C", description = "強調・倒置・省略・同格・挿入・共通関係"),
            TopicEntity(id = TU, name = "語法・語彙", parentId = T, colorHex = "#9A3412", description = "動詞語法・前置詞・接頭辞接尾辞・コロケーション"),
            TopicEntity(id = TR, name = "読解", parentId = T, colorHex = "#7C2D12", description = "パラグラフリーディング・ディスコースマーカー"),
            TopicEntity(id = TW, name = "英作文・音声", parentId = T, colorHex = "#FB923C", description = "自由英作文・和文英訳・発音アクセント"),
        ),
        definitions = listOf(
            // 文法
            g("現在完了", "【定義】have+過去分詞。継続（for/since）・経験（ever/never/before/once）・完了結果（just/already/yet）。【注意】明確な過去を表す語（yesterday, ago, when〜?）とは共起しない。"),
            g("過去完了", "【定義】had+過去分詞。過去のある時点より前（大過去）、過去までの継続経験完了。【例】The train had left when I arrived."),
            g("未来完了", "【定義】will have+過去分詞。未来のある時点までの完了・経験・継続。【例】By next year, I will have lived here for ten years."),
            g("進行形にしない動詞", "【定義】状態動詞（know, belong, resemble, like, want, contain）。【例外】動作の意味なら進行形可: He is being kind.（いつになく親切）, I'm having lunch."),
            g("時・条件の副詞節", "【定義】when/if/until/as soon as などの副詞節では未来のことも現在形。【区別】名詞節の if/when は will 可: I don't know if it will rain."),
            g("助動詞+have+過去分詞", "【定義】過去への推量・後悔。must have p.p.（〜したに違いない）, can't have p.p.（〜したはずがない）, may have p.p., should have p.p.（〜すべきだったのに）, needn't have p.p.（〜する必要はなかったのに実際した）。"),
            g("助動詞の重要表現", "【定義】would rather A than B, may well（〜するのももっともだ）, might as well A as B（BするくらいならAする方がまし）, cannot help -ing, cannot 〜 too（いくら〜してもしすぎない）, used to（過去の習慣・状態）vs would（過去の習慣のみ）。"),
            g("受動態の応用", "【定義】進行形受動 be being p.p.、完了受動 have been p.p.、群動詞 be laughed at、SVOO の二重受動、SVOC の受動 be made to do（使役動詞は to が復活）、by 以外: be known to/for/as, be covered with, be surprised at."),
            g("不定詞の形容詞用法・副詞用法", "【定義】形容詞用法（something to drink・関係）、副詞用法（目的 in order to、結果 grow up to be / only to、感情の原因 glad to、判断の根拠 must be crazy to、条件 to hear him talk）。"),
            g("不定詞の意味上の主語", "【定義】for A to do。人の性質を表す形容詞（kind, careless, foolish）では of A to do。【例】It is kind of you to help me."),
            g("原形不定詞", "【定義】知覚動詞（see/hear/feel/watch + O + 原形/現在分詞/過去分詞）、使役動詞（make/have/let + O + 原形; get は to 不定詞）。help + O + (to) do。"),
            g("動名詞と不定詞の使い分け", "【定義】動名詞のみ: enjoy, mind, finish, avoid, give up, escape, practice, consider（メガフェプス）。不定詞のみ: want, hope, decide, expect, refuse, manage。意味が変わる: remember/forget/regret/try/stop + -ing（過去・実行）/to do（未来・未実行）。"),
            g("完了不定詞・完了動名詞", "【定義】to have p.p. / having p.p. で主節より前の時を表す。【例】He seems to have been ill.（病気だったようだ）= It seems that he was ill."),
            g("分詞の形容詞用法", "【定義】現在分詞=能動・進行（a sleeping baby, the man standing there）、過去分詞=受動・完了（a broken window, a letter written in English）。感情動詞は -ing=させる、-ed=させられる（exciting/excited）。"),
            g("分詞構文の意味と形", "【定義】時・理由・付帯状況・条件・譲歩。否定は Not -ing。完了 Having p.p.。受動 (Being) p.p.。独立分詞構文（主語が異なる: Weather permitting）。慣用: generally speaking, judging from, considering, frankly speaking。"),
            g("仮定法過去・過去完了", "【定義】現在の反実: If S 過去形, S would/could/might 原形。過去の反実: If S had p.p., S would have p.p.。混合型: If I had taken the medicine then, I would be fine now."),
            g("仮定法の重要表現", "【定義】I wish + 仮定法（〜ならなあ）、as if + 仮定法、It is time + 仮定法過去、If it were not for / Had it not been for（〜がなければ）、without/but for、if 省略の倒置（Were I / Had I）、should/were to（未来の仮定）、否則 otherwise。"),
            g("関係代名詞 what", "【定義】先行詞を含む「〜すること・もの」= the thing(s) which。【慣用】what is called（いわゆる）, what is more（さらに）, A is to B what C is to D, what S is（現在のS）, what S used to be。"),
            g("関係副詞", "【定義】when（時）, where（場所）, why（the reason）, how（the way; the way how は不可）。前置詞+which に書き換え可。先行詞または関係副詞の省略。"),
            g("制限用法と非制限用法", "【定義】非制限（コンマ付き）は先行詞を補足説明。that は不可。which は前文全体を受けられる。【例】He has two sons, who are doctors.（息子は2人だけ）。"),
            g("複合関係詞", "【定義】whoever/whichever/whatever（名詞節=anyone who、譲歩副詞節=no matter who）、whenever/wherever/however（譲歩・任意）。"),
            g("原級・比較級・最上級の重要表現", "【定義】not so much A as B（AというよりB）、as many as（〜もの数）、no more than（=only）、not more than（=at most）、no less than（=as many as）、not less than（=at least）、the 比較級, the 比較級、比較級 and 比較級、no 比較級 than（クジラ構文）。"),
            g("最上級相当表現", "【定義】No other A is as 〜 as B / 比較級 than B。Nothing is more 〜 than。B is 比較級 than any other A。"),
            g("倒置の型", "【定義】否定語句文頭（Never have I seen）、Only+副詞句、so/neither/nor（So do I）、場所方向副詞（Here comes the bus）、仮定法 if 省略、補語文頭（Happy is the man who）、Not until 〜 did S V。"),
            g("強調構文と形式主語の識別", "【定義】It is X that 〜: X を除いて文が成立すれば強調構文（Xは名詞・副詞句）。It is 形容詞 that 〜 は形式主語。"),
            g("話法の転換", "【定義】直接→間接: 人称・時制・指示語（this→that, here→there, now→then, yesterday→the day before）を変換。疑問文は if/whether や疑問詞+SV。命令文は tell/ask + O + to do。"),
            g("名詞節を導く接続詞", "【定義】that（〜ということ）, whether/if（〜かどうか）, 疑問詞節。前置詞の後は whether のみ。同格の that（the fact that 〜, the idea that 〜）。"),
            g("譲歩の表現", "【定義】though/although, even if/even though, while/whereas（対比）, as（形容詞+as S V: Young as he is）, whether A or B, no matter wh-, 命令文 or, for all（〜にもかかわらず）。"),
            g("冠詞の重要用法", "【定義】the+形容詞（the rich=富裕層）、the+単位（by the hour）、a+固有名詞（a Mr. Smith/an Edison）、無冠詞（go to school, by bus, at table）、総称の3形（A dog is / Dogs are / The dog is）。"),
            g("可算名詞と不可算名詞", "【定義】不可算: information, advice, furniture, baggage/luggage, news, equipment, homework, evidence。数える: a piece of。many/few は可算、much/little は不可算。"),
            g("代名詞の用法", "【定義】it/one/that: one=同種の不特定、that=同種の特定（the 〜 of）、it=同一物。other/another/the other/the others/others。each other（代名詞）。most of the / almost all。"),
            g("否定の重要表現", "【定義】部分否定 not all/always/necessarily。準否定 hardly/scarcely/seldom/rarely。二重否定。never 〜 without -ing。the last person to do（決して〜しない人）。far from, anything but（決して〜ない）, nothing but（〜だけ）。"),
            // 構文
            s("無生物主語構文の訳し方", "【定義】S が原因・手段のとき「Sのせいで/によって O は〜」と副詞的に訳す。make/enable/allow/prevent/remind/cause/lead/force/deprive。【例】The heavy rain prevented us from going out."),
            s("同格構文", "【定義】名詞の言い換え。コンマ、of（the city of Tokyo）、that 節（the news that 〜）、to 不定詞（the ability to）、ダッシュ、namely、such as。"),
            s("挿入と省略", "【定義】挿入: S, I think, V / as it were / if any / if ever。省略: 副詞節内の S+be（when (he was) young）、共通語句、比較の後半、to 不定詞の代不定詞（I'd love to）。"),
            s("共通関係と等位接続", "【定義】A and B が同じ語句に共通してかかる。both A and B, either A or B, neither A nor B, not only A but (also) B, not A but B, B as well as A では A と B は文法的に対等。"),
            s("It 〜 to / that 構文", "【定義】形式主語 It is 〜 to do/that 節。形式目的語 S V it C to do/that 節（find/make/think it easy to）。"),
            s("so 〜 that / such 〜 that", "【定義】so+形容詞副詞+that、such+(a)+形容詞+名詞+that。程度と結果。so that S can（目的）と区別。倒置 So 〜 that."),
            s("分離・離れた修飾", "【定義】主語と述語の間に長い修飾（関係詞節・分詞句・同格）が挟まる。名詞の直後に前置詞句が2重3重にかかる。動詞を先に見つけて主部の範囲を確定する。"),
            s("as の識別", "【定義】接続詞（時・理由・比例・様態・譲歩）、関係代名詞（such 〜 as, the same 〜 as, as is often the case）、前置詞（〜として）、比較 as 〜 as。"),
            s("that の識別", "【定義】接続詞（名詞節・同格・so that）、関係代名詞（先行詞あり・不完全文）、指示形容詞/代名詞、副詞（that much）。"),
            s("to 不定詞 vs 前置詞 to", "【定義】look forward to -ing, be used to -ing, object to -ing, when it comes to -ing, devote oneself to -ing, in addition to -ing, with a view to -ing は前置詞 to。"),
            // 語法
            u("自動詞と他動詞の頻出", "【定義】他動詞（前置詞不要）: discuss, marry, enter, reach, approach, resemble, mention, attend, answer, obey。自動詞（前置詞必要）: apologize to, arrive at, graduate from, complain about, object to, agree with."),
            u("SVOO をとらない動詞", "【定義】explain/suggest/propose/introduce/describe/announce は explain A to B の形のみ。"),
            u("tell/say/speak/talk の区別", "【定義】tell + 人 + that/物、say + that/物（say to 人）、speak/talk は自動詞（speak to/with 人、talk about）。speak English は他動詞用法。"),
            u("lie/lay/rise/raise", "【定義】lie-lay-lain（横たわる・自）, lie-lied-lied（嘘）, lay-laid-laid（横たえる・他）, rise-rose-risen（自）, raise-raised-raised（他）。"),
            u("borrow/lend/rent/use", "【定義】borrow（無料で借りる・持ち運べる）, lend（貸す）, rent（有料で借りる・貸す）, use（トイレ・電話を借りる）。"),
            u("rob/steal, blame/accuse", "【定義】rob 人 of 物、steal 物 from 人。blame 人 for 事、accuse 人 of 事。provide/supply 人 with 物、deprive 人 of 物、inform 人 of 事、remind 人 of 事。"),
            u("感情を表す他動詞", "【定義】surprise, excite, bore, interest, disappoint, satisfy, please, annoy は「〜させる」。人が主語なら be -ed、物が主語なら -ing。"),
            u("前置詞のコアイメージ", "【定義】at（点）, on（接触）, in（内部）, by（そば・期限まで）, until（継続の終点）, for（方向・交換・期間）, of（分離・所属）, from（起点）, to（到達点）, with（同伴・道具）, over（覆う）, above（上方）。"),
            u("接頭辞・接尾辞", "【定義】接頭辞: re-（再）, un-/in-/im-/il-/ir-/dis-（否定）, pre-（前）, post-（後）, sub-（下）, super-（上）, trans-（越えて）, inter-（間）, co-/con-（共に）, ex-（外）。接尾辞: -tion/-ment/-ness/-ity（名詞）, -ful/-less/-ous/-ive/-able（形容詞）, -ize/-fy/-en（動詞）, -ly（副詞）。"),
            u("語源で覚える重要語幹", "【定義】spect（見る: inspect, respect, prospect）, port（運ぶ: import, transport）, dict（言う: predict, contradict）, ject（投げる: reject, project）, duct（導く: conduct, produce）, scrib（書く: describe, subscribe）, vis/vid（見る）, cede/ceed（行く）。"),
            u("紛らわしい語（形が似た語）", "【定義】adopt/adapt, affect/effect, principal/principle, stationary/stationery, complement/compliment, lie/lay, quite/quiet, desert/dessert, personal/personnel, economic/economical, respectable/respectful/respective, sensible/sensitive, considerable/considerate, imaginary/imaginative/imaginable."),
            u("コロケーション（動詞+名詞）", "【定義】make a decision/mistake/effort, do homework/damage/harm, take a picture/break/risk, have a look/rest, pay attention, keep a diary/promise, catch a cold, commit a crime, raise a question, draw a conclusion。"),
            u("句動詞（基本動詞）", "【定義】put off（延期）, put up with（我慢）, call off（中止）, look up to（尊敬）, look down on（軽蔑）, turn down（断る）, bring up（育てる・持ち出す）, come up with（思いつく）, run out of（切らす）, get over（克服）, make up for（埋め合わせ）, carry out（実行）, figure out（理解）, take after（似ている）。"),
            u("数量表現", "【定義】a number of（多くの・複数扱い）vs the number of（数・単数扱い）, a great deal of（不可算）, plenty of, a couple of, dozens of, hundreds of, quite a few（かなり多くの）, only a few（ほんの少し）, few（ほとんどない）。"),
            u("主語と動詞の一致", "【定義】each/every/either+単数、A as well as B は A に一致、not only A but B / either A or B は B に一致、the number of は単数、a number of は複数、集合名詞 family/team は単複両用、学問名（mathematics/physics）単数、距離金額時間はひとまとまりで単数。"),
            // 読解
            r("パラグラフリーディング", "【定義】1段落1主張。トピックセンテンス（多くは冒頭）→サポート（具体例・理由・データ）→結論。抽象→具体の流れを追い、具体例は主張を確認する材料として速読。"),
            r("ディスコースマーカー", "【定義】逆接（however, but, yet, nevertheless, on the other hand, in contrast）、追加（moreover, furthermore, in addition, also）、例示（for example, for instance, such as）、因果（therefore, thus, hence, as a result, consequently）、順序（first, then, finally）、要約（in short, in conclusion, to sum up）、言い換え（in other words, that is）。"),
            r("譲歩→逆接の主張パターン", "【定義】It is true that 〜 / Of course 〜 / Some people say 〜 / Certainly 〜 の後に but/however が来て筆者の主張が現れる。譲歩部分は筆者の意見ではない。"),
            r("指示語・代名詞の照応", "【定義】it/this/that/they/such/the former/the latter/the one/the other が何を指すかは直前の名詞句を単複・性で絞り、代入して意味が通るか確認。this は前文全体を受けることが多い。"),
            r("スラッシュリーディングと英語の語順", "【定義】意味のかたまり（主部/述部/目的語/前置詞句/節）ごとに区切り、返り読みせず前から理解する。SVを見つける→修飾語句を後ろから足すのではなく前から処理。"),
            r("因果関係の表現", "【定義】A cause/lead to/result in/bring about/contribute to/give rise to B（A→B）。B result from/stem from/be due to/be attributed to A（B←A）。because of/owing to/thanks to/on account of。"),
            r("言い換え（パラフレーズ）の把握", "【定義】設問の選択肢は本文の語を別語で言い換える。同義語（important=crucial=vital=essential）、名詞化（decide→decision）、能動⇔受動、具体⇔抽象の変換に注意。"),
            r("評論文の頻出テーマ", "【定義】環境（climate change, sustainability, biodiversity）、テクノロジー（AI, automation, privacy）、言語（bilingualism, communication）、教育・心理（motivation, cognitive bias）、社会（globalization, diversity, aging society）。背景知識が速読を支える。"),
            r("物語文の読み方", "【定義】登場人物・場面・時間の把握→心情変化の追跡（感情語・行動・比喩）→転機の特定。設問は「なぜその行動をとったか」「気持ちの変化」が中心。"),
            // 英作文・音声
            w("自由英作文の構成", "【定義】主張（I think 〜 / I agree with 〜）→理由（First, 〜 Second, 〜）→具体例（For example）→結論（For these reasons, 〜）。1文は短く、抽象語を具体例で支える。指定語数の±10%。"),
            w("和文英訳の手順", "【定義】①日本語を「誰が何をどうする」の英語向き構造に和文和訳 ②主語と動詞を決める ③時制・冠詞・単複を確認 ④知らない語は簡単な語で言い換え（回避）⑤見直しチェック（三単現・a/the・前置詞）。"),
            w("英作文で減点される典型ミス", "【定義】三単現の -s 落ち、冠詞抜け、可算名詞の無冠詞単数、時制の不統一、almost people（→most people）、I think so that、discuss about、日本語直訳の enjoy to、because 節の独立、コンマスプライス。"),
            w("パラグラフ・ライティングの結束", "【定義】接続語（However, In addition, Therefore）、代名詞での言及、キーワードの反復、旧情報→新情報の順で文を並べる。"),
            w("発音記号と母音", "【定義】/æ/（cat）/ʌ/（cut）/ɑː/（car）/ɔː/（call）/ə/（about・弱母音）/iː/（seat）/ɪ/（sit）/uː/（pool）/ʊ/（pull）/eɪ//aɪ//ɔɪ//aʊ//oʊ/（二重母音）。"),
            w("アクセントの規則", "【定義】-tion/-sion/-ic/-ical/-ity/-ial/-ious の直前にアクセント。-ee/-eer/-ese/-oo は語尾。名前動後（récord/recórd, présent/presént, óbject/objéct）。-ate 動詞は2つ前。"),
            w("リスニングの音変化", "【定義】連結（an apple→アナポー）、脱落（next day の t）、同化（did you→ディジュ）、弱形（and→n, to→tə, can→kən）、フラップ t（water→ワラ）。"),
            w("イディオム（頻出200のうち核）", "【定義】in terms of, as for, in spite of, regardless of, on behalf of, at the expense of, in favor of, in charge of, by means of, in the long run, at the same time, to some extent, as a matter of fact, on the contrary, for the time being, sooner or later, all of a sudden, on purpose, by accident, in vain。"),
        ),
        thoughts = listOf(
            DemoData.DemoThought("英文法は「動詞の形」の体系として覚える", "時制・助動詞・受動態・準動詞・仮定法は全部「動詞がどんな形をとるか」の話。テーブルを1枚作る: 時（現在/過去/未来）×相（単純/進行/完了/完了進行）×態（能動/受動）×法（直説/仮定）。不定詞・動名詞・分詞は動詞が名詞・形容詞・副詞の席に座るときの制服だと考えると、分詞構文も完了不定詞も同じ表の延長に置ける。"),
            DemoData.DemoThought("長文は「対比」と「因果」の矢印を書き込む", "評論文はほぼ A vs B の対比と A→B の因果で構成されている。段落頭のディスコースマーカーだけ見て、余白に⇔と→を書いていくと、設問の8割は矢印の上にある。譲歩（It is true / Of course）は必ず逆接が続くので、そこが主張の位置。"),
            DemoData.DemoThought("英作文は「言えることを言う」競技", "書きたいことを英語にするのではなく、自信のある構文に日本語を寄せる。SVO＋because＋for example の3点セットで書けない主張はない。知らない単語は「上位語＋説明」で回避（kimono→traditional Japanese clothing）。"),
        ),
        quizzes = listOf(
            Quiz("I ( ) him since 2010. に入るのは？", "have known", listOf("have known", "know", "knew", "am knowing"), "since があるので現在完了の継続。know は状態動詞で進行形不可。"),
            Quiz("If it ( ) tomorrow, we will stay home.", "rains", listOf("rains", "will rain", "rained", "would rain"), "時・条件の副詞節では現在形。"),
            Quiz("You ( ) have told me earlier.（言ってくれればよかったのに）", "should", listOf("should", "must", "can't", "may"), "should have p.p.=〜すべきだったのに。"),
            Quiz("I remember ( ) the door.（閉めたことを覚えている）", "locking", listOf("locking", "to lock", "lock", "locked"), "remember -ing=過去にしたことを覚えている。"),
            Quiz("It is kind ( ) you to help me.", "of", listOf("of", "for", "to", "with"), "人の性質を表す形容詞は of。"),
            Quiz("( ) I known his address, I would have written to him.", "Had", listOf("Had", "If", "Have", "Were"), "if 省略の倒置（仮定法過去完了）。"),
            Quiz("This is ( ) I wanted.（これが私が欲しかったものだ）", "what", listOf("what", "which", "that", "whose"), "先行詞なし→what。"),
            Quiz("He is ( ) a scholar than a writer.", "not so much", listOf("not so much", "no more", "not more", "much more"), "not so much A as B=AというよりB。"),
            Quiz("( ) have I seen such a beautiful sunset.", "Never", listOf("Never", "Ever", "Always", "Often"), "否定語文頭で倒置。"),
            Quiz("We discussed ( ) the problem.", "（何も入らない）", listOf("（何も入らない）", "about", "on", "of"), "discuss は他動詞。"),
            Quiz("The number of students ( ) increasing.", "is", listOf("is", "are", "have been", "were"), "the number of は単数。"),
            Quiz("I'm looking forward to ( ) you.", "seeing", listOf("seeing", "see", "have seen", "be seen"), "この to は前置詞。"),
            Quiz("「しかしながら」を表すディスコースマーカーでないのは？", "therefore", listOf("therefore", "however", "nevertheless", "yet"), "therefore は因果。"),
            Quiz("-tion で終わる語のアクセント位置は？", "-tion の直前の音節", listOf("-tion の直前の音節", "第1音節", "最終音節", "-tion の2つ前"), "informátion, educátion。"),
            Quiz("The news ( ) us.（その知らせは私たちを驚かせた）", "surprised", listOf("surprised", "was surprised", "surprising", "surprises at"), "surprise は他動詞「驚かせる」。"),
            Quiz("no more than 10 の意味は？", "たった10（=only）", listOf("たった10（=only）", "多くとも10（=at most）", "少なくとも10", "10も"), "not more than=at most。"),
        ),
        connections = listOf(
            Conn("現在完了", "過去完了", "prerequisite"), Conn("過去完了", "未来完了", "related"),
            Conn("時制の一致", "話法の転換", "prerequisite"), Conn("進行形にしない動詞", "現在完了", "related"),
            Conn("助動詞", "助動詞+have+過去分詞", "extends"), Conn("助動詞", "助動詞の重要表現", "extends"),
            Conn("態", "受動態の応用", "extends"), Conn("不定詞", "不定詞の形容詞用法・副詞用法", "extends"),
            Conn("不定詞", "不定詞の意味上の主語", "extends"), Conn("不定詞", "原形不定詞", "extends"),
            Conn("動名詞", "動名詞と不定詞の使い分け", "extends"), Conn("不定詞", "完了不定詞・完了動名詞", "extends"),
            Conn("分詞", "分詞の形容詞用法", "extends"), Conn("分詞構文", "分詞構文の意味と形", "extends"),
            Conn("仮定法", "仮定法過去・過去完了", "extends"), Conn("仮定法", "仮定法の重要表現", "extends"),
            Conn("仮定法の重要表現", "倒置の型", "related"), Conn("倒置", "倒置の型", "extends"),
            Conn("関係詞", "関係代名詞 what", "extends"), Conn("関係詞", "関係副詞", "extends"),
            Conn("関係詞", "制限用法と非制限用法", "extends"), Conn("関係詞", "複合関係詞", "extends"),
            Conn("比較", "原級・比較級・最上級の重要表現", "extends"), Conn("比較", "最上級相当表現", "extends"),
            Conn("強調構文", "強調構文と形式主語の識別", "extends"), Conn("否定", "否定の重要表現", "extends"),
            Conn("無生物主語", "無生物主語構文の訳し方", "extends"), Conn("冠詞", "冠詞の重要用法", "extends"),
            Conn("接続詞", "名詞節を導く接続詞", "extends"), Conn("接続詞", "譲歩の表現", "extends"),
            Conn("前置詞", "前置詞のコアイメージ", "extends"), Conn("語法(頻出動詞)", "自動詞と他動詞の頻出", "extends"),
            Conn("語法(頻出動詞)", "SVOO をとらない動詞", "extends"), Conn("5文型", "自動詞と他動詞の頻出", "prerequisite"),
            Conn("as の識別", "that の識別", "related"), Conn("to 不定詞 vs 前置詞 to", "動名詞と不定詞の使い分け", "related"),
            Conn("接頭辞・接尾辞", "語源で覚える重要語幹", "related"), Conn("主語と動詞の一致", "数量表現", "related"),
            Conn("パラグラフリーディング", "ディスコースマーカー", "prerequisite"), Conn("ディスコースマーカー", "譲歩→逆接の主張パターン", "extends"),
            Conn("パラグラフリーディング", "自由英作文の構成", "related"), Conn("パラグラフ・ライティングの結束", "ディスコースマーカー", "references"),
            Conn("和文英訳の手順", "英作文で減点される典型ミス", "related"), Conn("発音記号と母音", "アクセントの規則", "related"),
            Conn("アクセントの規則", "リスニングの音変化", "related"), Conn("因果関係の表現", "無生物主語構文の訳し方", "related"),
        ),
        tags = mapOf(
            "仮定法の重要表現" to listOf("英語", "文法", "頻出"),
            "動名詞と不定詞の使い分け" to listOf("英語", "文法", "頻出"),
            "関係代名詞 what" to listOf("英語", "文法", "頻出"),
            "原級・比較級・最上級の重要表現" to listOf("英語", "文法", "頻出"),
            "ディスコースマーカー" to listOf("英語", "読解"),
            "自由英作文の構成" to listOf("英語", "英作文"),
            "アクセントの規則" to listOf("英語", "音声"),
        ),
        wikis = listOf(
            WikiArticleEntity(
                title = "高校英語 全単元マップ", summary = "文法・構文・語法・読解・英作文・音声",
                contentMd = """
# 高校英語

## 文法（動詞の形）
時制: [[現在完了]] [[過去完了]] [[未来完了]] [[進行形にしない動詞]] [[時・条件の副詞節]]
助動詞: [[助動詞+have+過去分詞]] [[助動詞の重要表現]] / 態: [[受動態の応用]]
準動詞: [[不定詞の形容詞用法・副詞用法]] [[不定詞の意味上の主語]] [[原形不定詞]] [[動名詞と不定詞の使い分け]] [[完了不定詞・完了動名詞]] [[分詞の形容詞用法]] [[分詞構文の意味と形]]
仮定法: [[仮定法過去・過去完了]] [[仮定法の重要表現]]
関係詞: [[関係代名詞 what]] [[関係副詞]] [[制限用法と非制限用法]] [[複合関係詞]]
比較: [[原級・比較級・最上級の重要表現]] [[最上級相当表現]]
その他: [[倒置の型]] [[強調構文と形式主語の識別]] [[話法の転換]] [[名詞節を導く接続詞]] [[譲歩の表現]] [[冠詞の重要用法]] [[可算名詞と不可算名詞]] [[代名詞の用法]] [[否定の重要表現]]

## 構文
[[無生物主語構文の訳し方]] [[同格構文]] [[挿入と省略]] [[共通関係と等位接続]] [[It 〜 to / that 構文]] [[so 〜 that / such 〜 that]] [[分離・離れた修飾]] [[as の識別]] [[that の識別]] [[to 不定詞 vs 前置詞 to]]

## 語法・語彙
[[自動詞と他動詞の頻出]] [[SVOO をとらない動詞]] [[tell/say/speak/talk の区別]] [[lie/lay/rise/raise]] [[borrow/lend/rent/use]] [[rob/steal, blame/accuse]] [[感情を表す他動詞]] [[前置詞のコアイメージ]] [[接頭辞・接尾辞]] [[語源で覚える重要語幹]] [[紛らわしい語（形が似た語）]] [[コロケーション（動詞+名詞）]] [[句動詞（基本動詞）]] [[数量表現]] [[主語と動詞の一致]]

## 読解
[[パラグラフリーディング]] [[ディスコースマーカー]] [[譲歩→逆接の主張パターン]] [[指示語・代名詞の照応]] [[スラッシュリーディングと英語の語順]] [[因果関係の表現]] [[言い換え（パラフレーズ）の把握]] [[評論文の頻出テーマ]] [[物語文の読み方]]

## 英作文・音声
[[自由英作文の構成]] [[和文英訳の手順]] [[英作文で減点される典型ミス]] [[パラグラフ・ライティングの結束]] [[発音記号と母音]] [[アクセントの規則]] [[リスニングの音変化]] [[イディオム（頻出200のうち核）]]
""".trimIndent()
            )
        ),
        boardTitle = "高校英語 — 文法・構文・語法・読解・英作文",
        boardHubs = listOf(
            Triple("動詞の形", listOf("現在完了", "助動詞+have+過去分詞", "動名詞と不定詞の使い分け", "分詞構文の意味と形"), "#FFEDD5"),
            Triple("仮定法・関係詞・比較", listOf("仮定法の重要表現", "関係代名詞 what", "原級・比較級・最上級の重要表現", "倒置の型"), "#FED7AA"),
            Triple("構文", listOf("無生物主語構文の訳し方", "強調構文と形式主語の識別", "as の識別", "that の識別"), "#FDBA74"),
            Triple("語法", listOf("自動詞と他動詞の頻出", "前置詞のコアイメージ", "句動詞（基本動詞）", "主語と動詞の一致"), "#FB923C"),
            Triple("読解・英作文", listOf("パラグラフリーディング", "ディスコースマーカー", "自由英作文の構成", "アクセントの規則"), "#F97316"),
        )
    )
}
