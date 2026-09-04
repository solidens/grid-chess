# F-Droid status

Not submitted. One thing has to change first, recorded here so the finding is
not re-derived later.

## What already passes

Dependencies are clean. F-Droid's [inclusion policy](https://f-droid.org/docs/Inclusion_Policy/)
explicitly permits prebuilt artefacts from Maven Central, Google Maven and
JitPack, which covers ONNX Runtime (MIT) and chesslib (Apache-2.0, JitPack).
Everything in the tree is FLOSS, there is no analytics or ad SDK, and the app
holds no permissions at all.

`fastlane/metadata/android/en-US/` holds the title, descriptions, changelog,
icon and screenshots in the layout F-Droid reads from the app repository.

## The blocker: the networks are not in the repository

F-Droid builds from a clean checkout of a git tag on their own build server. A
clean clone of `v0.1.0` contains `assets/policy_index.txt` and no
`assets/models/` at all, so their build would produce an APK where
`MaiaEngine.ensureSession()` fails, logs, and the engine falls back to random
legal moves — an app that looks fine and plays nothing.

`tools/export_maia.py` cannot close the gap. It fetches the weights over the
network and shells out to an `lc0` binary that is not in F-Droid's build image,
and the metadata reference is explicit that "nothing should be built during this
prebuild phase".

So submitting means committing the five converted `.onnx` files — 17.4 MB at
f32, about half that at f16 — into the repository, and that decision has not
been taken. The licence side is fine either way: the weights are GPL-3, this
project is GPL-3, and F-Droid allows freely-licensed non-functional assets.

## Also worth knowing before submitting

F-Droid signs with its own key. Anyone who installed the GitHub APK cannot
upgrade to the F-Droid build in place — they would have to uninstall first —
unless the build is made reproducible and F-Droid is asked to ship the upstream
signature.

Submission is a merge request against
[fdroiddata](https://gitlab.com/fdroid/fdroiddata) and needs a GitLab account,
plus a `metadata/com.gridchess.yml` build recipe. Review is a conversation with
F-Droid maintainers, so it wants the app to be past its first few hours.
