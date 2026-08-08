# Publishing

Releases use the same Maven Central flow as `ffmpeg-ffm`.

1. Replace the snapshot version in the parent and child poms with the release
   version, for example `3.14.1-0.1.0`.
2. Run `mvn clean install` locally and push the commit and matching
   `v3.14.1-0.1.0` tag.
3. Publish a non-prerelease GitHub Release for that tag.

The release workflow validates that the tag exactly matches the Maven version,
builds and tests all three classifier jars, combines them into one Central
deployment, signs all artifacts, and waits for publication. Repository secrets
must provide `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`,
`GPG_PRIVATE_KEY`, and `GPG_PASSPHRASE`.

