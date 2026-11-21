@echo off
setlocal
pushd "%~dp0..\relay"
call npx.cmd --yes wrangler@4 deploy --config wrangler.jsonc
set "T1_DEPLOY_EXIT=%ERRORLEVEL%"
popd
exit /b %T1_DEPLOY_EXIT%
