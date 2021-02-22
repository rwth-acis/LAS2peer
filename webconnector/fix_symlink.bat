@echo off 
del %2 /Q
xcopy %1 %2 /i /c /k /e /r /y