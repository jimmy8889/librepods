#!/usr/bin/env python3
"""Exercise the real local server with no Bluetooth hardware or audio service."""
import json, os, pathlib, socket, subprocess, tempfile, time, sys
build=pathlib.Path(sys.argv[1]).resolve()
with tempfile.TemporaryDirectory(prefix='librepods-check-') as tmp:
    env=os.environ.copy()
    runtime=pathlib.Path(tmp)/'runtime';runtime.mkdir(mode=0o700)
    env.update(QT_QPA_PLATFORM='offscreen',XDG_RUNTIME_DIR=str(runtime),XDG_CONFIG_HOME=tmp+'/config')
    with open(pathlib.Path(tmp)/'app.log','w') as log:
        app=subprocess.Popen([str(build/'librepods'),'--hide'],env=env,stdout=log,stderr=log)
        try:
            path=str(runtime/'librepods.sock')
            end=time.monotonic()+10
            while not pathlib.Path(path).exists():
                assert app.poll() is None, 'App quit before IPC started'
                assert time.monotonic()<end, 'No IPC socket'
                time.sleep(.05)
            def cli(cmd):
                return subprocess.run([str(build/'librepods-ctl'),cmd],env=env,text=True,capture_output=True,timeout=4)
            first=cli('status');assert first.returncode==0,first.stderr
            state=json.loads(first.stdout)
            assert state['connected'] is False and state['left'] is None and state['case'] is None
            command=cli('noise:anc');assert command.returncode==1,command
            assert json.loads(command.stdout)['error']=='AirPods are disconnected'
            duplicate=subprocess.run([str(build/'librepods'),'--hide'],env=env,text=True,capture_output=True,timeout=5)
            assert duplicate.returncode==0, duplicate.stderr
            assert cli('status').returncode==0, 'Second instance broke first socket'
            with socket.socket(socket.AF_UNIX) as watcher:
                watcher.settimeout(3);watcher.connect(path);watcher.sendall(b'watch\n')
                assert json.loads(watcher.recv(4096))['connected'] is False
            with socket.socket(socket.AF_UNIX) as fragmented:
                fragmented.settimeout(3);fragmented.connect(path)
                fragmented.sendall(b'sta');fragmented.sendall(b'tus\n')
                assert json.loads(fragmented.recv(4096))['connected'] is False
            with socket.socket(socket.AF_UNIX) as oversized:
                oversized.settimeout(3);oversized.connect(path);oversized.sendall(b'x'*300+b'\n')
                assert oversized.recv(4096)==b''
            assert cli('status').returncode==0, 'Invalid request broke server'
            print('IPC PASS: status, disconnected error, single instance, subscription, fragmented and oversized requests')
        finally:
            app.terminate()
            app.wait(timeout=5)
