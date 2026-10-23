# Limen privacy policy

**Published at <https://peringer.ch/legal/limen/privacy/>**, in five languages.

The five canonical source files were updated for Android 1.2.0 on 13 October 2026, covering the address lookup (a switch then, automatic since 1.2.1 (6)), supporting documents, recovery retention and PDF/HTML copies. These targeted corrections are model-authored and are not proof of native review or website deployment. The previous translation `sourceHash` is retained as historical provenance; a full provider retranslation/native review has not been represented as completed. See the remediation ledger for the release state.

## This file is a pointer, not a copy

The policy used to be kept here as readable Markdown and republished by hand as
HTML in a separate repository. Two copies of a legal document drift, and they had:
by September 2026 the published pages, the data safety notes and this file
disagreed about what the apps did.

The source now lives once, in the peringer.ch repository at
`src/content/legal/limen/`, one MDX file per language. The site builds the
pages from it, so the published policy and its source cannot disagree.

## The old URL is gone

`alp78.github.io/legal/limen/privacy/` was the first home and no longer resolves.
Retiring a published privacy URL is normally forbidden, because Google Play holds
one for the life of a listing. It was safe here only because the Play entries were
deleted before any of them reached a reader, so no listing ever carried it. Nothing
may point at that host again.

## When to change it

A change in what the app collects, sends or stores changes the policy, and the
policy moves before the release does. Edit the English at
`src/content/legal/limen/en.mdx` in the peringer.ch repository, then retranslate:
the translation script keys on a hash of the English, so a changed source is
picked up and an unchanged one is skipped.

Check the claims against the signed release rather than against the source.
`aapt2 dump permissions` on the built artefact is what a reviewer can also run,
and it is the only way to catch a permission that arrived through a dependency.
