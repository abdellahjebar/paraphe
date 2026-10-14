# Domain model

The concepts of the product, how they relate, and the rules that decide a revision's status.
Names follow [the glossary](../product/glossary.md).

## Concepts

```
Project ──< WorkPackage ──────────────┐ default reviewers
   │                                  ▼
   ├──< Participant (role, specialty, contact)
   ├──< OpinionStatus (code, label, effect)
   ├── members: AppUser
   └──< Document ──< Revision (index label, sequence, file, fingerprint)
                          │
                          ├──< ReviewRequest   who must answer (the "expected" reviewers)
                          │         └──< ReviewLink    personal secret link, one active
                          └──< Opinion          who answered what, appended only
```

| Concept | Meaning | Mutable? |
|---|---|---|
| Project | A construction site | name never changes after creation |
| WorkPackage | A trade or part of the work (Lot 01, GENERAL) | no |
| Participant | A person or firm with a role and a contact | can be deactivated, never deleted |
| OpinionStatus | A status a reviewer may give, with an **effect** | can be deactivated; past opinions keep their snapshot |
| Document | A subject (a drawing, a note) | no |
| **Revision** | One version of a document, with its PDF | **never**: it is a fact |
| ReviewRequest | A reviewer a revision was sent to | no |
| ReviewLink | A reviewer's personal link | only "first opened" and "revoked" are written, once |
| **Opinion** | A reviewer's answer on one revision | **never**: appended only |

## The four effects

A status means nothing to the rules except its effect.

| Effect | Example | Meaning |
|---|---|---|
| `APPROVING` | VSO | Approved |
| `APPROVING_WITH_RESERVES` | VAO | Approved if points are fixed |
| `INFORMATIVE` | VPI | Taken note of; validates nothing, blocks nothing |
| `BLOCKING` | NC, REF | Must be reworked |

## How a revision's status is derived

`RevisionReview` considers the **latest** opinion of each expected reviewer:

1. any `BLOCKING` → `TO_REVISE`, even if others have not answered;
2. else an expected reviewer has not answered → `PENDING`;
3. else any `APPROVING_WITH_RESERVES` → `APPROVED_WITH_RESERVES`;
4. else any `APPROVING` → `APPROVED`;
5. else (only informative opinions) → `ACKNOWLEDGED`.

Consequences worth remembering:

- only an **expected** reviewer can ever be "late": a reviewer who was not asked is never waiting;
- an informative opinion beside approvals is ignored;
- answering again replaces the opinion in the calculation, and keeps the old one in the history;
- an opinion from someone not expected is refused by the domain **and** by a foreign key.

The dashboard adds two states that are not revision statuses: `NO_REVISION` (a document with no
revision yet) and `NOT_SENT` (a revision with no review opened).

## Revisions

- `label` is free text ("A", "REV1"); it is unique per document, case-insensitively.
- `sequence` starts at 1 and orders revisions; the highest is the current one.
- A revision is **superseded** when a higher sequence exists. Answers on a superseded revision are
  kept and flagged, never refused: that is what happens on site.
- The fingerprint (SHA-256) is stored, so a file can always be proven unchanged.

## Review links

- One active (not revoked) link per reviewer and revision; issuing a new one revokes the old one.
- State is derived: `ISSUED`, `OPENED`, `ANSWERED`, `EXPIRED`, `REVOKED` (revocation beats expiry,
  which beats the reviewer's actions).
- An unusable link (unknown, expired, revoked) always looks the same from outside.

## Entered on behalf

The contractor may record an answer received by phone or message. The opinion carries
`enteredBy`, and the journal's actor is the person who typed it, so the proof never suggests the
reviewer used their own link.

## Invariants kept by the database, not only by the code

| Invariant | Mechanism |
|---|---|
| Journal, opinions, revisions and links are never modified or deleted | `forbid_change()` triggers, **and** the application role holds no UPDATE, DELETE or TRUNCATE on them |
| An opinion's reviewer was asked on that revision | composite foreign key |
| A reviewer belongs to the project of the revision | trigger on `review_request` |
| A document's work package belongs to its project | composite foreign key |
| A default reviewer belongs to the work package's project | composite foreign keys |
| One active link per reviewer and revision | partial unique index |
| A participant has a contact; an email is stored normalised | check constraints |
