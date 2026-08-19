# Security policy

Please report security-sensitive issues privately to the repository owner
instead of opening a public issue with exploit details.

The JoiPlay library permission has `normal` protection so a separately signed
companion can request it. The provider is a convenience boundary, not a secret
vault: any installed app declaring the permission can read the sanitized
metadata. It must remain read-only and must never expose save data or arbitrary
private files.

