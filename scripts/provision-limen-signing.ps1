param(
  [string]$KeyStore = "$env:USERPROFILE\.android\limen-upload.jks",
  [string]$PropertiesFile = "$env:USERPROFILE\.gradle\limen-signing.properties"
)

$ErrorActionPreference = 'Stop'
if ((Test-Path -LiteralPath $KeyStore) -or (Test-Path -LiteralPath $PropertiesFile)) {
  throw 'Limen signing material already exists; refusing to replace it.'
}

$keyStoreParent = Split-Path -Parent $KeyStore
$propertiesParent = Split-Path -Parent $PropertiesFile
[IO.Directory]::CreateDirectory($keyStoreParent) | Out-Null
[IO.Directory]::CreateDirectory($propertiesParent) | Out-Null

$randomBytes = [byte[]]::new(36)
[Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
$secret = [Convert]::ToBase64String($randomBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$env:LIMEN_PROVISIONING_SECRET = $secret
try {
  & keytool `
    -genkeypair `
    -keystore $KeyStore `
    -storetype PKCS12 `
    -alias limen-upload `
    -keyalg RSA `
    -keysize 4096 `
    -sigalg SHA256withRSA `
    -validity 10000 `
    -dname 'CN=Alexis Peringer, O=PERINGER, L=Lausanne, C=CH' `
    '-storepass:env' LIMEN_PROVISIONING_SECRET `
    '-keypass:env' LIMEN_PROVISIONING_SECRET
  if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE" }

  $portableKeyStore = $KeyStore.Replace('\', '/')
  $properties = @(
    "storeFile=$portableKeyStore"
    "storePassword=$secret"
    'keyAlias=limen-upload'
    "keyPassword=$secret"
  )
  [IO.File]::WriteAllLines($PropertiesFile, $properties, [Text.UTF8Encoding]::new($false))
} catch {
  if (Test-Path -LiteralPath $KeyStore) { Remove-Item -LiteralPath $KeyStore -Force }
  if (Test-Path -LiteralPath $PropertiesFile) { Remove-Item -LiteralPath $PropertiesFile -Force }
  throw
} finally {
  Remove-Item Env:LIMEN_PROVISIONING_SECRET -ErrorAction SilentlyContinue
  [Array]::Clear($randomBytes, 0, $randomBytes.Length)
  $secret = $null
}

Write-Output "Created Limen upload keystore: $KeyStore"
Write-Output "Created external signing configuration: $PropertiesFile"
