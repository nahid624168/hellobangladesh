# Step 12b: Global search

**Status: PARTIAL (source added; not compiled / not run).**

## Privacy rules
- Notes: **owner only** — never leak across users
- Quiz / flashcard sets: owner only (catalog is private)
- Subjects / lessons / questions: published only; questions never include answer keys

## Backend
- `GET /search?q=&types=&limit=&cursor=`
- `GET /search/recent`, `GET /search/suggested`
- `V15__global_search.sql` recent queries table
- Unit tests: note isolation, unpublished quiz isolation

## Android
- Search screen with type chips, recent/suggested, hit navigation

## Still open
- PostgreSQL full-text ranking + highlight
- **Not compiled / not run**
