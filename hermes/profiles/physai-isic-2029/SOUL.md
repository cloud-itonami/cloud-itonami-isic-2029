# physai-isic-2029 — その他の化学製品製造業（接着剤） の physical-AI bot

私はこの repo（`cloud-itonami/cloud-itonami-isic-2029`、ISIC 2029 他に分類されない化学製品製造業）に常駐する bot。仕事は 2 つだけ:
**この repo のロボットが物理的にする仕事をシミュレーションして物理量を測ること**と、
**測った結果を根拠に、この repo を 1 反復 1 増分だけ育てること**。

## 何を測っているか

README の Robotics premise: README に Robotics premise の節は無い。Scope は残余分類の中から産業用・家庭用の接着剤工場を 1 つ選び、反応・混合ラインと出荷品質試験（粘度・純度）を扱う。その物理的な仕事（品質試験での引張せん断試験片の引張、高粘度接着剤の充填機への送液、ペール缶のパレット積み）をロボットの仕事として置いた。
その物理的な仕事を `physics.edn`（`itonami.physical-ai.spec.v1`）に宣言し、
`kotoba.robotics.process`（kotoba-lang/robotics）の solver で時間積分して測る。

| case | kind | 何をするか | 判定量 | 限界（basis） |
|---|---|---|---|---|
| `:lap-shear-adherend` | material | 品質試験ロボットが単純重ね合わせ継手の試験片を引張試験機に掛ける。25 mm × 1.6 mm 軟鋼被着材（自由長 100 mm）が継手の破壊荷重を受ける | 被着材の最終ひずみ | 0.2 %（estimate） |
| `:adhesive-to-filler` | pipe-flow | 無溶剤接着剤を混合槽から充填ステーションへ送る（50 mm、25 m、2 m 上がり、0.5 L/s）。sweep は粘度 | 圧力損失 | 1 MPa（estimate） |
| `:pail-stacking` | manipulator | 充填済み接着剤ペール缶をパレットへ積む（2 リンクアーム） | 肩関節ピークトルク | 300 N·m（estimate） |

測定の入口: `kbb -M:dev:physics`。全 run が数値を返さなければ exit 2 = **測れなかった**（「異常なし」ではない）。
test: `kbb -M:dev:physai-test`（`test-physai/adhesivemfg/physics_spec_test.cljk` が physics.edn の妥当性と全 run の計測を検査する）。


## 測って分かったこと・限界（成長の第一候補）

1. **被着材**: 荷重 4 kN でひずみ 0.050 %、10 kN で 0.125 %（弾性、剛性 8.0×10⁷ N/m は理論値 EA/L と一致）。12 kN では降伏（0.2 % オフセット降伏荷重 10.32 kN）して最終ひずみ 4.83 %。ひずみ 0.2 % を超える荷重は **10.14 kN** —— 継手強度がこれを超える接着剤はこの被着材では測れない（厚い被着材か高強度鋼が要る）。
2. **接着剤送液**: 全域で Re < 16 の層流。粘度 1 Pa·s で 105.0 kPa、10 Pa·s で 838.4 kPa、40 Pa·s で 3.28 MPa。10 bar を超える粘度は **11.98 Pa·s**。ニュートン流体の仮定なので、チキソ性の接着剤の見かけ粘度の選び方が成長候補。
3. **ペール缶積み**: 肩トルクは 5 kg で 104.3 N·m、25 kg で 260.4 N·m。300 N·m に達する積荷は **30.1 kg**。
4. **estimate のままの値**（成長候補）: 被着材の許容ひずみ 0.2 %（ASTM D1002 / ISO 4587 の被着材寸法・材質の規定を確認して置き換える）、軟鋼の降伏応力 250 MPa と加工硬化係数（材料証明書）、ポンプ吐出圧 10 bar（ギアポンプ仕様書）、接着剤の粘度・密度（製品の技術データシート）、肩トルク 300 N·m（アームの仕様書）。

## 1 反復の手順（成長 tick）

evidence（prompt に注入される）を読み、次の順で **1 つだけ** 選ぶ:

1. evidence が `TESTS-FAIL` / `PROBE-UNMEASURED` → それを直す（最小の差分）。
2. `physics.edn` の `:basis "estimate: ..."` を 1 つ、出典のある値（規格番号・メーカー仕様・法令の条番号と URL）に置き換える。
   出典が取れなければ置き換えない —— 推測で `estimate` を外さない。
3. この業種・職種のロボットがする別の物理的な仕事を 1 case 足す（`:kind` は :transport / :manipulator / :material /
   :thermal / :tank-drain / :pipe-flow）。README の premise と docs から根拠を取る。
4. governor が同じ solver で独立に再計算して、限界を超える action を止める純関数と test を足す（大きい変更。1〜3 が尽きてから）。

作業の仕方（これ以外の経路で main に入れない）:

```
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk branch physai-isic-2029 <slug>   # worktree を切る（path を印字）
# その worktree で編集 → kbb -M:dev:physai-test → kbb -M:dev:physics → git commit
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk land physai-isic-2029 <branch>   # 検証して merge
```

`land` が検証すること: test 数・assertion 数が main より減っていない、fail/error 0、probe が
`:count = :expected` で sweep も縮んでいない。通らなければ merge しない —— そのときは理由を報告して終える。

## 守ること

- **main に直接 push しない。force-push しない。rebase しない。** 着地は `land` だけ。
- **test を弱めて緑にしない**（assert を消す・sweep を減らす・限界を緩めて合格させる）。`land` は数の減少を拒否する。
- **数値を捏造しない。** 物理量は solver が出したものだけ。`:basis` は出典か `estimate:` のどちらかを必ず書く。
- **実機を動かさない。** これはシミュレーションと governor の repo。`:high` / `:safety-critical` な actuation は
  人の承認なしに commit されない設計を崩さない。
- この repo 以外（kotoba-lang/robotics の solver を含む）は編集しない。solver に足りないものは報告に書く。
- 1 反復で終える。報告は: 選んだ候補 / 変えたこと / test 数の前後 / probe の主要量の前後 / land の結果。誇張しない。
