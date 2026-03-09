"""
NOVA TV - Flask server para Android (Chaquopy)
Este archivo corre dentro del APK via Chaquopy
"""
import threading
import os
import sys

_server_thread = None

def start_server(files_dir: str):
    """Llamado desde MainActivity.kt para iniciar Flask."""
    global _server_thread

    # Configurar paths
    static_path = os.path.join(files_dir, 'static')
    url_file = os.path.join(files_dir, 'novatv_url.txt')

    os.makedirs(static_path, exist_ok=True)

    # Iniciar Flask en thread daemon
    def run():
        _run_flask(static_path, url_file)

    _server_thread = threading.Thread(target=run, daemon=True)
    _server_thread.start()
    # Esperar que arranque
    import time; time.sleep(1.5)

def _run_flask(static_path: str, url_file: str):
    import urllib.request
    import re
    import json
    from flask import Flask, jsonify, request, send_from_directory

    app = Flask(__name__, static_folder=static_path)

    state = {
        'items': [],
        'saved_url': '',
        'url_file': url_file
    }

    # Cargar URL guardada
    try:
        if os.path.exists(url_file):
            with open(url_file) as f:
                state['saved_url'] = f.read().strip()
    except: pass

    VOD_EXT = re.compile(r'\.(mp4|mkv|avi|mov|wmv|flv|ts|m2ts|webm|mpg|mpeg)(\?.*)?$', re.I)

    def is_vod(url): return bool(VOD_EXT.search(url))

    def parse_m3u(text):
        items, cur = [], {}
        for line in text.splitlines():
            line = line.strip()
            if line.startswith('#EXTINF:'):
                cur = {'name':'Item','group':'Sin grupo','logo':'','url':'','vod':False}
                if ',' in line: cur['name'] = line.split(',',1)[1].strip() or 'Item'
                for tag, key in [('tvg-name','name'),('group-title','group'),('tvg-logo','logo')]:
                    m = re.search(rf'{tag}="([^"]*)"', line, re.I)
                    if m and m.group(1): cur[key] = m.group(1).strip()
            elif line and not line.startswith('#') and cur:
                cur['url'] = line; cur['vod'] = is_vod(line)
                items.append(cur); cur = {}
        return items

    def fetch_m3u(url):
        url = url.replace('github.com','raw.githubusercontent.com').replace('/blob/','/')
        req = urllib.request.Request(url, headers={'User-Agent':'Mozilla/5.0 VLC/3.0'})
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.read().decode('utf-8', errors='replace')

    @app.route('/')
    def index():
        return send_from_directory(static_path, 'index.html')

    @app.route('/api/status')
    def status():
        return jsonify({
            'items': len(state['items']),
            'saved_url': state['saved_url'],
            'live': sum(1 for c in state['items'] if not c['vod']),
            'vod':  sum(1 for c in state['items'] if  c['vod']),
        })

    @app.route('/api/load', methods=['POST'])
    def load_url():
        data = request.get_json()
        url = data.get('url','').strip()
        if not url: return jsonify({'error':'URL vacía'}), 400
        try:
            text  = fetch_m3u(url)
            items = parse_m3u(text)
            if not items: return jsonify({'error':'Sin canales'}), 400
            state['items'] = items
            state['saved_url'] = url
            try:
                with open(url_file,'w') as f: f.write(url)
            except: pass
            return jsonify({'ok':True,'total':len(items),
                'live':sum(1 for c in items if not c['vod']),
                'vod': sum(1 for c in items if  c['vod'])})
        except Exception as e:
            return jsonify({'error':str(e)}), 500

    @app.route('/api/channels')
    def channels():
        q = request.args.get('q','').lower()
        items = [c for c in state['items'] if not c['vod']]
        if q: items = [c for c in items if q in c['name'].lower()]
        return jsonify(items)

    BAD_RE = re.compile(r'^(sin grupo|)$', re.I)
    SER_RE = re.compile(r'serie|season|temporada|episode|ep\b', re.I)

    @app.route('/api/movies')
    def movies():
        q     = request.args.get('q','').lower()
        group = request.args.get('group','')
        items = [c for c in state['items'] if c['vod'] and not BAD_RE.match(c['group'].strip()) and not SER_RE.search(c['group'])]
        if group: items = [c for c in items if c['group']==group]
        if q:     items = [c for c in items if q in c['name'].lower()]
        return jsonify(items)

    @app.route('/api/series')
    def series():
        q     = request.args.get('q','').lower()
        group = request.args.get('group','')
        items = [c for c in state['items'] if c['vod'] and SER_RE.search(c['group'])]
        if group: items = [c for c in items if c['group']==group]
        if q:     items = [c for c in items if q in c['name'].lower()]
        return jsonify(items)

    @app.route('/api/groups')
    def groups():
        from collections import Counter
        mode = request.args.get('mode','movies')
        if mode == 'series':
            items = [c for c in state['items'] if c['vod'] and SER_RE.search(c['group'])]
        else:
            items = [c for c in state['items'] if c['vod'] and not BAD_RE.match(c['group'].strip()) and not SER_RE.search(c['group'])]
        counts = Counter(c['group'] for c in items)
        return jsonify([{'name':k,'count':v} for k,v in sorted(counts.items())])

    app.run(host='127.0.0.1', port=5000, debug=False, threaded=True, use_reloader=False)
