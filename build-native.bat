@echo off
echo ========================================
echo Compilacao Nativa GraalVM - Versao Corrigida
echo ========================================

REM Define variáveis de ambiente
set GRAALVM_HOME=C:\GraalVM\graalvm-community-openjdk-24.0.2+11.1
set JAVA_HOME=%GRAALVM_HOME%
set PATH=%GRAALVM_HOME%\bin;%PATH%

echo.
echo Verificando instalacao do GraalVM...
where native-image >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERRO: native-image nao encontrado!
    echo Instalando componente native-image...
    call gu install native-image
    if %ERRORLEVEL% neq 0 (
        echo Falha ao instalar native-image. Verifique sua instalacao do GraalVM.
        pause
        exit /b 1
    )
)

echo.
echo Passo 1: Limpando build e locks anteriores...
call gradlew clean

REM Remove arquivo de lock se existir
if exist "src\main\resources\META-INF\native-image\com.waldirbaia\agente-backup\.lock" (
    echo Removendo arquivo .lock antigo...
    del /F "src\main\resources\META-INF\native-image\com.waldirbaia\agente-backup\.lock"
)

REM Remove diretório de configuração temporário
if exist "build\native-config" (
    echo Removendo configuracoes temporarias antigas...
    rmdir /S /Q "build\native-config"
)

echo.
echo Passo 2: Criando estrutura de diretorios...
if not exist "src\main\resources\META-INF\native-image\com.waldirbaia\agente-backup" (
    mkdir "src\main\resources\META-INF\native-image\com.waldirbaia\agente-backup"
)

if not exist "build\native-config" (
    mkdir "build\native-config"
)

echo.
echo Passo 3: Compilando JAR com Shadow...
call gradlew shadowJar
if %ERRORLEVEL% neq 0 (
    echo ERRO ao criar o JAR!
    pause
    exit /b 1
)

echo.
echo Passo 4: Gerando configuracoes com o agente (OPCIONAL)...
echo Pressione ENTER para pular ou Y para executar com o agente:
set /p RUN_AGENT=

if /i "%RUN_AGENT%"=="Y" (
    echo Executando com agente...
    echo IMPORTANTE: Execute algumas operacoes na aplicacao e depois feche
    timeout /t 3
    call gradlew generateNativeImageConfig
    
    if %ERRORLEVEL% neq 0 (
        echo Aviso: Falha ao executar com agente. Continuando sem configuracoes do agente...
    )
)

echo.
echo Passo 5: Copiando arquivos de configuracao manual...
REM Copia os arquivos de configuração que criamos manualmente
if exist "reflect-config.json" (
    copy /Y "reflect-config.json" "src\main\resources\META-INF\native-image\com.waldirbaia\agente-backup\"
)
if exist "resource-config.json" (
    copy /Y "resource-config.json" "src\main\resources\META-INF\native-image\com.waldirbaia\agente-backup\"
)
if exist "native-image.properties" (
    copy /Y "native-image.properties" "src\main\resources\META-INF\native-image\com.waldirbaia\agente-backup\"
)

echo.
echo Passo 6: Compilando imagem nativa...
echo Isso pode demorar varios minutos. Por favor, aguarde...

REM Tenta compilar com configurações básicas primeiro
call gradlew nativeCompile

if %ERRORLEVEL% neq 0 (
    echo.
    echo ========================================
    echo Primeira tentativa falhou. Tentando com todas as classes em build-time...
    echo ========================================
    
    REM Cria um arquivo temporário com configuração mais agressiva
    echo Args = --no-fallback ^
--initialize-at-build-time ^
-H:+ReportExceptionStackTraces ^
--enable-url-protocols=http,https,ws,wss ^
-H:+AddAllCharsets ^
-H:+AllowIncompleteClasspath ^
--report-unsupported-elements-at-runtime > "src\main\resources\META-INF\native-image\com.waldirbaia\agente-backup\native-image.properties"
    
    REM Tenta novamente
    call gradlew nativeCompile
    
    if %ERRORLEVEL% neq 0 (
        echo.
        echo ========================================
        echo ERRO na compilacao!
        echo ========================================
        echo.
        echo Possiveis solucoes:
        echo 1. Verifique se o Visual Studio Build Tools esta instalado (Windows)
        echo 2. Tente executar este script como Administrador
        echo 3. Verifique os logs em: build\native\native-image\
        echo.
        pause
        exit /b 1
    )
)

echo.
echo ========================================
echo Compilacao concluida com sucesso!
echo ========================================
echo Executavel gerado em: build\native\nativeCompile\agente-backup.exe
echo.
echo Testando o executavel...
if exist "build\native\nativeCompile\agente-backup.exe" (
    echo Tamanho do arquivo: 
    for %%F in ("build\native\nativeCompile\agente-backup.exe") do echo %%~zF bytes
    echo.
    echo Deseja executar o programa agora? (S/N)
    set /p RUN_APP=
    if /i "%RUN_APP%"=="S" (
        start "" "build\native\nativeCompile\agente-backup.exe"
    )
) else (
    echo AVISO: Executavel nao encontrado no local esperado!
)
echo.
pause