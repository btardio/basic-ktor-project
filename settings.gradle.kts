rootProject.name = "email-verifier"

include(
    "applications:webserver",
    "applications:collector",
    "applications:analyzer",

    "components:serialization-support",
    "components:test-support",
    "components:env-support",
)
