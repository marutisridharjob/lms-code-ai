# Prebuilt artifacts

Convenience copies of the build output so you can install without running Maven.
These may lag behind the sources — the canonical artifacts are produced by
`mvn clean verify` (locally) or by the **Build** GitHub Actions workflow
(downloadable from the run's *Artifacts* section).

| File | What it is |
|---|---|
| `lms-code-ai-<version>-dropins.zip` | `eclipse/plugins` + `eclipse/features` layout — extract into `<eclipse>/dropins/lms-code-ai/` and restart with `eclipse -clean` |
| `com.lmscode.ai_<version>.jar` | The plugin bundle on its own (self-contained, Gson embedded) — can be dropped into `<eclipse>/dropins/` |
| `com.lmscode.ai.feature_<version>.jar` | The feature jar |
| `lms-code-ai-<version>-updatesite.zip` | p2 update site for `Help → Install New Software… → Add… → Archive…` |
