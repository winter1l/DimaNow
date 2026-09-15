# Checkpoint - Samsung metadata-only experiment completed - 2026-09-14

Observed: keeping com.example.dimanow and adding only com.samsung.android.support.ongoing_activity=true did not enable Samsung Now Bar with the developer test option OFF on the user's API 37 / One UI 9 phone. Android promotion flag was present in all tested cases; Samsung Showing-list membership and the physically rendered card were observed in the baseline ON control only. App notification settings never gained Live information. See nowbar-metadata-experiment-20260914.md for precise evidence, APK hashes, and limits.

Cleanup complete: exact pre-experiment Debug APK restored via install -r and re-pull hash verified; both temporary settings restored to original 1; probe ID 6229 and test package removed; source metadata/probe reverted; narrow debug/test builds succeeded. The original optimized-artifact question Q-009 remains separate. No production source diff, phone preference/data change, commit, release, or other-app changes retained.

Unknown next research: additional Samsung eligibility conditions under the app's real identity; metadata alone was insufficient in a package-replacement test. A cold reboot was not authorized or attempted. Prior Q-007/Q-008 closure checkpoint archived in checkpoints/2026-09-14-0210-before-nowbar-metadata-experiment.md.
