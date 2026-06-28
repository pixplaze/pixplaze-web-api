# Uncomment on macOS openssl=LibreSSL (for compatibility):
# export PATH="$(brew --prefix openssl@3)/bin:$PATH"

openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out priv.pem
openssl pkey -in priv.pem -pubout -out pub.pem