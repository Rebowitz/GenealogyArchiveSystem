// Browser uploader for Genealogy Archive System (Phase 2, P2-A).
// Each file is sent as a raw PUT body to its own item key, so a resent request is recognised by the
// server and never archived twice. "Safely archived" is shown only once the server reports the
// verified stored copy has been committed (archived === true).
(function () {
    'use strict';
    var BASE = location.pathname.replace(/\/(files|preservation)\/.*$/, '');

    function itemKey(file) {
        // Stable per source file within a batch: re-choosing the same files after a failure reuses keys.
        var s = file.name + '|' + file.size + '|' + file.lastModified, h1 = 0x811c9dc5, h2 = 0;
        for (var i = 0; i < s.length; i++) {
            h1 = Math.imul(h1 ^ s.charCodeAt(i), 16777619) >>> 0;
            h2 = (Math.imul(h2, 31) + s.charCodeAt(i)) >>> 0;
        }
        return 'k' + h1.toString(16) + h2.toString(16) + '-' + file.size.toString(36);
    }

    function row(table, name) {
        var tr = document.createElement('tr'), a = document.createElement('td'), b = document.createElement('td');
        a.textContent = name;
        b.textContent = 'Waiting';
        tr.appendChild(a); tr.appendChild(b);
        table.tBodies[0].appendChild(tr);
        return b;
    }

    function send(url, form, file, headers, cell) {
        return new Promise(function (resolve) {
            var xhr = new XMLHttpRequest();
            xhr.open('PUT', url);
            xhr.setRequestHeader(form.dataset.csrfHeader, form.dataset.csrf);
            xhr.setRequestHeader('Content-Type', 'application/octet-stream');
            xhr.setRequestHeader('X-File-Name', encodeURIComponent(file.name));
            xhr.setRequestHeader('X-File-Last-Modified', String(file.lastModified));
            Object.keys(headers).forEach(function (k) { if (headers[k]) { xhr.setRequestHeader(k, headers[k]); } });
            xhr.upload.onprogress = function (e) {
                if (e.lengthComputable) { cell.textContent = 'Receiving ' + Math.floor(100 * e.loaded / e.total) + '%'; }
            };
            xhr.onload = function () {
                var body = {};
                try { body = JSON.parse(xhr.responseText); } catch (ignored) { /* not JSON */ }
                resolve({ status: xhr.status, body: body });
            };
            xhr.onerror = function () { resolve({ status: 0, body: { error: 'The connection failed. Choose the file again to retry.' } }); };
            xhr.send(file);
        });
    }

    function poll(statusUrl, cell) {
        return fetch(statusUrl, { credentials: 'same-origin' }).then(function (r) { return r.json(); }).then(function (s) {
            show(cell, s);
            var waiting = !s.archived && ['RECEIVING', 'STAGED', 'PROMOTING', 'PROMOTED'].indexOf(s.state) >= 0;
            if (waiting) {
                return new Promise(function (ok) { setTimeout(ok, 1500); }).then(function () { return poll(statusUrl, cell); });
            }
            return s;
        });
    }

    function show(cell, s) {
        cell.textContent = s.message || s.state;
        if (s.archived && s.fileId) {
            var a = document.createElement('a');
            a.href = BASE + '/files/' + s.fileId;
            a.textContent = ' open';
            cell.appendChild(a);
        }
    }

    var uploader = document.getElementById('uploader');
    if (uploader) {
        uploader.addEventListener('submit', function (ev) {
            ev.preventDefault();
            var files = Array.prototype.slice.call(document.getElementById('files').files);
            var max = Number(uploader.dataset.max);
            var table = document.getElementById('progress');
            var method = document.getElementById('derivationMethod');
            var headers = {
                'X-Restriction': document.getElementById('restricted').checked ? 'RESTRICTED' : 'NORMAL',
                'X-Source-Note': encodeURIComponent(document.getElementById('sourceNote').value || ''),
                'X-Parent-File': uploader.dataset.parent || '',
                'X-Derivation-Method': method ? encodeURIComponent(method.value) : ''
            };
            var base = BASE;
            var chain = Promise.resolve();
            files.forEach(function (file) {
                var cell = row(table, file.name);
                if (file.size > max) { cell.textContent = 'Not uploaded: larger than the limit of ' + max + ' bytes'; return; }
                chain = chain.then(function () {
                    // The same file keeps the same key, so a resend after a lost response never archives it
                    // twice. If an earlier attempt ended without archiving (abandoned, cancelled, rejected),
                    // a fresh attempt uses the next key.
                    function attempt(n) {
                        var key = itemKey(file) + (n > 1 ? '-a' + n : '');
                        var url = base + '/files/batches/' + uploader.dataset.batch + '/items/' + key;
                        return send(url, uploader, file, headers, cell).then(function (r) {
                            if (r.status !== 200) { cell.textContent = 'Not archived: ' + (r.body.error || ('HTTP ' + r.status)); return; }
                            var ended = ['ABANDONED', 'CANCELLED', 'REJECTED'].indexOf(r.body.state) >= 0;
                            if (ended && r.body.fresh !== true && n < 20) {
                                return attempt(n + 1);
                            }
                            show(cell, r.body);
                            return poll(base + '/files/operations/' + r.body.operationId + '/status', cell);
                        });
                    }
                    return attempt(1);
                });
            });
        });
    }

    var repair = document.getElementById('repair');
    if (repair) {
        repair.addEventListener('submit', function (ev) {
            ev.preventDefault();
            var file = document.getElementById('files').files[0];
            var out = document.getElementById('repairStatus');
            var base = BASE;
            send(base + '/preservation/contents/' + repair.dataset.content + '/repair', repair, file, {}, out).then(function (r) {
                if (r.status !== 200) { out.textContent = 'Refused: ' + (r.body.error || ('HTTP ' + r.status)); return; }
                return poll(base + '/preservation/operations/' + r.body.operationId + '/status', out);
            });
        });
    }
})();
