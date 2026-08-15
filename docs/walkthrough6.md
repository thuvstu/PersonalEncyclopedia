Personal Encyclopedia — クイズシステム最適化 (walkthrough6)
master(0ea5c17)上で、クイズシステム(表示・UI・動作柔軟性)を全面最適化しました。
「1機能1ビルド」「既存コードを唯一の正とする」方針に沿い、ラウンド単位で実装→検証→コミットしています。

方針(ユーザー質問はdismissされたため推奨案で実施)
- 出題形式は qa / mcq / fill_blank の3種に正式収束(sort/cloze/customは生成・出題対象外)
- サーバー(QuizRoutes)もアプリと同じ採点パイプラインに共通化
- 範囲: 出題最適化 / セッション柔軟化 / UI表示 / サーバー整合 / テスト の全部

実施コミット
R1+R2(21fc535): 出題ロジック + クイズ演習設定
- QuizDao: 出題プールを排他3分類に再構成
  苦手 = 不正解履歴あり・正解履歴なし / 未習 = 一度も回答していない / ランダム = 正解済みを除く全件
  全プールに topicId・difficultyMin・types を伝播(getWrongUnmasteredQuizzes / getNeverAttemptedQuizzes / getRandomUnmasteredQuizzes)
- QuizRepository.getNextQuizzes: 分類間の重複・正解済みの混入を排除し、distinctBy でセッション内重複防止
- SettingsRepository / ServerViewModel / SettingsScreen: クイズ演習設定を追加
  問題数(5〜20) / サバイバル上限(5〜50) / 難易度フィルタ(すべて〜むずかしめ) / 出題形式3種トグル / プレッシャー時間(15〜180秒) / ヒント減点率(0〜50%)
- QuizViewModel: 設定を read してセッションへ反映(難易度は difficultyMin<=1 なら null = フィルタなし)

R4(9c7637e 内): クイズUI最適化
- 形式・採点方式ラベルを日本語統一(quizTypeLabel / gradingMethodLabel)。生文字列出力を排除
- 答え合わせ画面: 進捗(問x/y)、あなたの回答カード、MCQは全選択肢に正解(✓正解)・あなたの回答を強調表示
- 出題画面: MCQ選択肢に①〜⑧、テキスト入力に ImeAction.Done、サバイバルは「未習(終了)」と明示
- セッション進行中の戻る操作に破棄確認ダイアログ(BackHandler + TopAppBar)
- 内容幅を最大640dpに制限(タブレット・大画面対応)
- DashboardScreen の重複ボタン(クイズ一覧×2・ホワイトボード×2)を2列構成に整理

R5(9c7637e 内): gradingContextJson の実データ投入(walkthrough5の残課題を解消)
- RubricParser.buildGradingContextJson(契約に沿ったビルダー)を追加
- RuleBasedQuizGenerator: qa=用語(keyword 0.4)+定義(concept 0.6)、reverse qa・fill_blank=用語(keyword 1.0)を書き出し
- LlmQuizGenerator: 記述式(qa/essay)は必須キーワードを書き出し

R6(9c7637e 内): 採点の共通化
- QuizGraderService を新設(多段採点→ルーブリック採点→意味的採点→スコア計算を一元化)
- QuizRepository.gradeAndRecord は QuizGraderService へ委譲(attempt挿入は従来通り)
- ServerDependencies / QuizRoutes も同じ QuizGraderService を使用(旧: MultiStageGrader単独・固定スコア)

R7(cb92311): テスト
- QuizGraderServiceTest(7本): 正確一致+速度ボーナス / ヒント減点 / 誤答-1 / 未習null・0点 / rubric昇格 / mcq非対象 / API未設定で意味的採点スキップ
- RuleBasedQuizGeneratorTest(4本): qa/reverse qa/fill_blankのgradingContextJson契約適合、mcqは"{}"

検証
- :app:compileDebugKotlin: 成功(KSPがDAOクエリを検証)
- :app:testDebugUnitTest: 全テスト green(93本 = 既存 + 新規11本)
- :app:assembleDebug: 成功

実装中に発見・対処した不具合
- getNeverAttemptedQuizzes のサブクエリでエイリアス未定義(qa.quizId)による KSP エラー → SELECT quizId に修正
- PowerShell Set-Content が UTF-8 を破損 → git checkout で復元し edit ツールで再適用(以後 Set-Content 不使用)
- テスト側の誤り2件: qa は rubric が常に走るため正確一致検証は mcq で実施 / fill_blank は定義文に用語を含める必要

残課題
- 端末内モデル実装(LocalGradingProviders)
- rubric スコアの部分点反映
- rubricRationale / evidenceJson の DB 永続化
- サバイバルで「未習(終了)」以外の救済オプション(例: スキップで続行)の要否検討
