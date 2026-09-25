Test-only RSA key pair. Used exclusively by the tests (the `test` profile,
src/test/resources/application-test.yml) to mint JWTs the app accepts. No
running environment trusts it, so committing the private half is deliberate
and harmless.
