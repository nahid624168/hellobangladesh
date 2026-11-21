# Step 23: Note versions, export and sharing

Builds on Step 20 backend (`GET /notes/{id}/versions`, `POST .../restore`) and Step 21 editor.

**Status: PARTIAL (source added; not compiled / not run).**

## Scope
In:
- **Version history screen**: list versions (newest first), restore → new server version with prior content
- **Simple comparison**: select two versions → older/newer title + body preview
- **Share / export as text**: `ACTION_SEND` plain text (`title`, version, body) from editor
- Editor toolbar: Share + History (when note has an id)
- EN + BN strings

Out:
- PDF generation (no PDF library wired; text share covers the “export/share” acceptance path)
- Pixel-diff or line-level merge UI
- Public share links / collaboration (notes remain private)

## Acceptance criteria
1. Restore calls backend and returns user to editor path with updated content on next load.
2. Share intent includes title and body text.
3. Selecting two versions shows both snapshots without mutating data.
4. Other users still cannot list versions (server 404).

## Not verified
Nothing compiled or run.

## Run it
```bash
# Open a saved note → History → pick version → Restore
# Editor → Share → system sheet
```
