# Publishing

Releases use the same Maven Central flow as `ffmpeg-ffm`.

1. Replace the snapshot version in the parent and child poms with the release
   version, for example `3.14.1-0.1.0`.
2. Run `./mvnw clean install` locally and push the commit and matching
   `v3.14.1-0.1.0` tag.
3. Publish a non-prerelease GitHub Release for that tag.

The release workflow validates that the tag exactly matches the Maven version,
builds and tests all three classifier jars, combines them into one Central
deployment, signs all artifacts, and waits for publication. Repository secrets
must provide `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`,
`GPG_PRIVATE_KEY`, and `GPG_PASSPHRASE`.


Builds use the Maven Wrapper (`./mvnw`), which pins Maven 3.9.16. Do not
switch the release to the runner's `mvn`: under Maven 3.10 the
central-publishing plugin (0.11.0) stages resolver files such as
`_remote.repositories` and `maven-metadata-local.xml` into the bundle, and
Central rejects it with "Bundle has content that does NOT have a .pom file".

To inspect the signed bundle CI would upload, run the Release workflow
manually (optionally with a throwaway `version`; by default the current version
without `-SNAPSHOT`, since snapshot deploys bypass the unreachable URL). It
builds the natives on all three platforms, then builds the bundle against an
unreachable Central URL with placeholder credentials and attaches
`central-bundle.zip` as a workflow artifact; nothing is published.
