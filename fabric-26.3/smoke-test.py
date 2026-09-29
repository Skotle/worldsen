"""Run the development server, generate chunks in all dimensions, and stop cleanly.

Requires an accepted run/eula.txt and JAVA_HOME pointing to Java 25.
Uses only this port's run directory; no installed Minecraft worlds are touched.
"""
from pathlib import Path
import os
import queue
import subprocess
import sys
import threading
import time

root = Path(__file__).resolve().parent

def verify_log(log_text):
    errors = ['Mixin apply for mod earthshape failed', 'Failed to start the minecraft server',
              'Exception generating new chunk', 'Encountered an unexpected exception',
              'Failed to load registries', 'Error loading registry data']
    assert 'Done (' in log_text, 'Server startup not observed.'
    assert not any(error in log_text for error in errors), 'Server error; inspect smoke-test.log.'
    assert 'System chat: Saved the game' in log_text, 'Flush-save did not complete.'
    assert 'Stopping server' in log_text and 'BUILD SUCCESSFUL' in log_text, 'Clean shutdown not observed.'
    for dimension in ['overworld', 'the_nether', 'the_end']:
        region = root / 'run/earthshape-smoke/dimensions/minecraft' / dimension / 'region'
        assert any(p.stat().st_size > 8192 for p in region.glob('*.mca')), f'No saved chunks: {dimension}'
    print('PASS: startup, generated chunks in all three dimensions, flush-save, and clean shutdown.')

if '--verify-log' in sys.argv:
    verify_log((root / 'smoke-test.log').read_text(encoding='utf-8'))
    raise SystemExit(0)

if 'eula=true' not in (root / 'run/eula.txt').read_text():
    raise SystemExit('Accept the Minecraft EULA in run/eula.txt before running.')
lines = queue.Queue()
process = subprocess.Popen(
    ['cmd.exe', '/c', str(root / 'gradlew.bat'), '--console=plain', 'runServer'],
    cwd=root, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace',
)

def read_output():
    for line in process.stdout:
        lines.put(line)
    lines.put(None)

threading.Thread(target=read_output, daemon=True).start()
started = None
stop_sent = False
done = False
deadline = time.monotonic() + 240
with (root / 'smoke-test.log').open('w', encoding='utf-8') as log:
    while time.monotonic() < deadline:
        try:
            line = lines.get(timeout=1)
        except queue.Empty:
            line = ''
        if line is None:
            break
        log.write(line)
        log.flush()
        if 'Done (' in line and started is None:
            done = True
            started = time.monotonic()
            for command in [
                'execute in minecraft:overworld run forceload add 0 0 31 31',
                'execute in minecraft:overworld run forceload add 10000 8000 10015 8015',
                'execute in minecraft:the_nether run forceload add 0 0',
                'execute in minecraft:the_end run forceload add 0 0',
            ]:
                process.stdin.write(command + '\n')
            process.stdin.flush()
        if started is not None and time.monotonic() - started > 25 and not stop_sent:
            process.stdin.write('save-all flush\nstop\n')
            process.stdin.flush()
            stop_sent = True
    else:
        process.stdin.write('stop\n')
        process.stdin.flush()
        raise SystemExit('Smoke test timed out; inspect smoke-test.log.')

code = process.wait(timeout=30)
log_text = (root / 'smoke-test.log').read_text(encoding='utf-8')
assert done and stop_sent and code == 0, 'Server startup/shutdown failed; inspect smoke-test.log.'
verify_log(log_text)
