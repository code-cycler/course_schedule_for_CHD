package com.example.course_schedule_for_chd_v002.util

/**
 * HTML 脚本注入工具类 (v70)
 *
 * 使用 shouldInterceptRequest 在 HTML 中注入脚本标签
 * 确保脚本在页面解析前执行，解决 evaluateJavascript 异步时序问题
 *
 * v70 关键改进：
 * 1. 添加 underscore.js 支持 (_.filter, _.where, _.reject 等)
 * 2. 优化 isHtmlRequest() 只拦截主页面
 *
 * v50 关键改进：
 * 1. 使用 HTML 拦截而非 evaluateJavascript
 * 2. 添加完整的 jQuery 方法（prev, next, remove, cookie 等）
 * 3. 添加完整的 struts2_jquery 支持
 * 4. 添加 CourseTable 和 TaskActivity 模拟
 */
object ScriptInjector {

    private const val TAG = "ScriptInjector"

    /**
     * 获取需要注入到 HTML head 开头的脚本标签
     * 包含完整的 jQuery、beangle、CourseTable 模拟
     */
    fun getHeadInjectionScript(): String {
        return """
            |<script>
            |(function() {
            |    'use strict';
            |
            |    // ==================== v50 jQuery 核心模拟（完整版）====================
            |    if (typeof window.jQuery === 'undefined' || typeof window.${'$'} === 'undefined') {
            |        var jQuery = function(selector) {
            |            return new jQuery.fn.init(selector);
            |        };
            |
            |        jQuery.fn = jQuery.prototype = {
            |            constructor: jQuery,
            |            length: 0,
            |            jquery: '1.12.4-simulated-v50',
            |
            |            init: function(selector) {
            |                if (!selector) return this;
            |
            |                if (typeof selector === 'string') {
            |                    if (selector.charAt(0) === '<' && selector.charAt(selector.length - 1) === '>') {
            |                        var temp = document.createElement('div');
            |                        temp.innerHTML = selector;
            |                        var children = temp.childNodes;
            |                        for (var i = 0; i < children.length; i++) {
            |                            this[i] = children[i];
            |                        }
            |                        this.length = children.length;
            |                    } else {
            |                        try {
            |                            var elements = document.querySelectorAll(selector);
            |                            for (var i = 0; i < elements.length; i++) {
            |                                this[i] = elements[i];
            |                            }
            |                            this.length = elements.length;
            |                        } catch (e) {
            |                            console.error('[jQuery v50] Selector error:', e);
            |                        }
            |                    }
            |                } else if (selector.nodeType) {
            |                    this[0] = selector;
            |                    this.length = 1;
            |                } else if (typeof selector === 'function') {
            |                    jQuery.ready(selector);
            |                } else if (Array.isArray(selector)) {
            |                    for (var i = 0; i < selector.length; i++) {
            |                        this[i] = selector[i];
            |                    }
            |                    this.length = selector.length;
            |                }
            |                return this;
            |            },
            |
            |            each: function(callback) {
            |                for (var i = 0; i < this.length; i++) {
            |                    if (callback.call(this[i], i, this[i]) === false) break;
            |                }
            |                return this;
            |            },
            |
            |            click: function(handler) {
            |                return this.on('click', handler);
            |            },
            |
            |            on: function(event, handler) {
            |                return this.each(function() {
            |                    this.addEventListener(event, handler);
            |                });
            |            },
            |
            |            off: function(event, handler) {
            |                return this.each(function() {
            |                    this.removeEventListener(event, handler);
            |                });
            |            },
            |
            |            ready: function(handler) {
            |                if (document.readyState !== 'loading') {
            |                    setTimeout(handler, 0);
            |                } else {
            |                    document.addEventListener('DOMContentLoaded', handler);
            |                }
            |                return this;
            |            },
            |
            |            addClass: function(className) {
            |                return this.each(function() {
            |                    if (this.classList) this.classList.add(className);
            |                });
            |            },
            |
            |            removeClass: function(className) {
            |                return this.each(function() {
            |                    if (this.classList) this.classList.remove(className);
            |                });
            |            },
            |
            |            hasClass: function(className) {
            |                for (var i = 0; i < this.length; i++) {
            |                    if (this[i].classList && this[i].classList.contains(className)) return true;
            |                }
            |                return false;
            |            },
            |
            |            toggleClass: function(className) {
            |                return this.each(function() {
            |                    if (this.classList) this.classList.toggle(className);
            |                });
            |            },
            |
            |            css: function(prop, value) {
            |                if (typeof prop === 'string' && value === undefined) {
            |                    return this[0] ? window.getComputedStyle(this[0])[prop] : undefined;
            |                }
            |                return this.each(function() {
            |                    if (typeof prop === 'object') {
            |                        for (var key in prop) { this.style[key] = prop[key]; }
            |                    } else {
            |                        this.style[prop] = value;
            |                    }
            |                });
            |            },
            |
            |            html: function(content) {
            |                if (content === undefined) return this[0] ? this[0].innerHTML : undefined;
            |                return this.each(function() { this.innerHTML = content; });
            |            },
            |
            |            text: function(content) {
            |                if (content === undefined) return this[0] ? this[0].textContent : undefined;
            |                return this.each(function() { this.textContent = content; });
            |            },
            |
            |            attr: function(name, value) {
            |                if (value === undefined) return this[0] ? this[0].getAttribute(name) : undefined;
            |                return this.each(function() { this.setAttribute(name, value); });
            |            },
            |
            |            removeAttr: function(name) {
            |                return this.each(function() { this.removeAttribute(name); });
            |            },
            |
            |            prop: function(name, value) {
            |                if (value === undefined) return this[0] ? this[0][name] : undefined;
            |                return this.each(function() { this[name] = value; });
            |            },
            |
            |            val: function(value) {
            |                if (value === undefined) return this[0] ? this[0].value : undefined;
            |                return this.each(function() { this.value = value; });
            |            },
            |
            |            // v50 新增: prev() 方法
            |            prev: function() {
            |                var result = [];
            |                this.each(function() {
            |                    var prev = this.previousElementSibling;
            |                    if (prev && result.indexOf(prev) === -1) result.push(prev);
            |                });
            |                return jQuery(result);
            |            },
            |
            |            // v50 新增: next() 方法
            |            next: function() {
            |                var result = [];
            |                this.each(function() {
            |                    var next = this.nextElementSibling;
            |                    if (next && result.indexOf(next) === -1) result.push(next);
            |                });
            |                return jQuery(result);
            |            },
            |
            |            // v50 新增: remove() 方法
            |            remove: function() {
            |                return this.each(function() {
            |                    if (this.parentNode) this.parentNode.removeChild(this);
            |                });
            |            },
            |
            |            parent: function() {
            |                var result = [];
            |                this.each(function() {
            |                    if (this.parentElement && result.indexOf(this.parentElement) === -1) {
            |                        result.push(this.parentElement);
            |                    }
            |                });
            |                return jQuery(result);
            |            },
            |
            |            parents: function(selector) {
            |                var result = [];
            |                this.each(function() {
            |                    var parent = this.parentElement;
            |                    while (parent) {
            |                        if (result.indexOf(parent) === -1) {
            |                            if (!selector || parent.matches(selector)) {
            |                                result.push(parent);
            |                            }
            |                        }
            |                        parent = parent.parentElement;
            |                    }
            |                });
            |                return jQuery(result);
            |            },
            |
            |            children: function(selector) {
            |                var result = [];
            |                this.each(function() {
            |                    var children = this.children;
            |                    for (var i = 0; i < children.length; i++) {
            |                        if (result.indexOf(children[i]) === -1) {
            |                            if (!selector || children[i].matches(selector)) {
            |                                result.push(children[i]);
            |                            }
            |                        }
            |                    }
            |                });
            |                return jQuery(result);
            |            },
            |
            |            find: function(selector) {
            |                var result = [];
            |                this.each(function() {
            |                    var found = this.querySelectorAll(selector);
            |                    for (var i = 0; i < found.length; i++) {
            |                        if (result.indexOf(found[i]) === -1) result.push(found[i]);
            |                    }
            |                });
            |                return jQuery(result);
            |            },
            |
            |            eq: function(index) {
            |                return jQuery(this[index >= 0 ? index : this.length + index]);
            |            },
            |
            |            first: function() { return jQuery(this[0]); },
            |
            |            last: function() { return jQuery(this[this.length - 1]); },
            |
            |            get: function(index) {
            |                return index === undefined ? Array.prototype.slice.call(this) : this[index];
            |            },
            |
            |            index: function() {
            |                if (!this[0]) return -1;
            |                var parent = this[0].parentNode;
            |                if (!parent) return -1;
            |                var children = parent.children;
            |                for (var i = 0; i < children.length; i++) {
            |                    if (children[i] === this[0]) return i;
            |                }
            |                return -1;
            |            },
            |
            |            hide: function() { return this.css('display', 'none'); },
            |
            |            show: function() { return this.css('display', ''); },
            |
            |            toggle: function() {
            |                return this.each(function() {
            |                    this.style.display = this.style.display === 'none' ? '' : 'none';
            |                });
            |            },
            |
            |            focus: function() { if (this[0] && this[0].focus) this[0].focus(); return this; },
            |
            |            blur: function() { if (this[0] && this[0].blur) this[0].blur(); return this; },
            |
            |            submit: function() { if (this[0] && this[0].submit) this[0].submit(); return this; },
            |
            |            append: function(content) {
            |                return this.each(function() {
            |                    if (typeof content === 'string') {
            |                        this.insertAdjacentHTML('beforeend', content);
            |                    } else if (content.nodeType) {
            |                        this.appendChild(content);
            |                    }
            |                });
            |            },
            |
            |            prepend: function(content) {
            |                return this.each(function() {
            |                    if (typeof content === 'string') {
            |                        this.insertAdjacentHTML('afterbegin', content);
            |                    } else if (content.nodeType) {
            |                        this.insertBefore(content, this.firstChild);
            |                    }
            |                });
            |            },
            |
            |            after: function(content) {
            |                return this.each(function() {
            |                    if (typeof content === 'string') {
            |                        this.insertAdjacentHTML('afterend', content);
            |                    }
            |                });
            |            },
            |
            |            before: function(content) {
            |                return this.each(function() {
            |                    if (typeof content === 'string') {
            |                        this.insertAdjacentHTML('beforebegin', content);
            |                    }
            |                });
            |            },
            |
            |            clone: function() {
            |                var result = [];
            |                this.each(function() {
            |                    if (this.cloneNode) result.push(this.cloneNode(true));
            |                });
            |                return jQuery(result);
            |            },
            |
            |            // v50 新增: serialize() 方法
            |            serialize: function() {
            |                var form = this[0];
            |                if (!form || !form.elements) return '';
            |                var parts = [];
            |                for (var i = 0; i < form.elements.length; i++) {
            |                    var el = form.elements[i];
            |                    if (el.name && !el.disabled && el.type !== 'file') {
            |                        if (el.type === 'checkbox' || el.type === 'radio') {
            |                            if (el.checked) parts.push(encodeURIComponent(el.name) + '=' + encodeURIComponent(el.value));
            |                        } else {
            |                            parts.push(encodeURIComponent(el.name) + '=' + encodeURIComponent(el.value));
            |                        }
            |                    }
            |                }
            |                return parts.join('&');
            |            }
            |        };
            |
            |        jQuery.fn.init.prototype = jQuery.fn;
            |
            |        // 静态方法
            |        jQuery.each = function(obj, callback) {
            |            if (Array.isArray(obj)) {
            |                for (var i = 0; i < obj.length; i++) {
            |                    if (callback.call(obj[i], i, obj[i]) === false) break;
            |                }
            |            } else {
            |                for (var key in obj) {
            |                    if (callback.call(obj[key], key, obj[key]) === false) break;
            |                }
            |            }
            |        };
            |
            |        jQuery.extend = function() {
            |            var target = arguments[0] || {};
            |            for (var i = 1; i < arguments.length; i++) {
            |                for (var key in arguments[i]) {
            |                    target[key] = arguments[i][key];
            |                }
            |            }
            |            return target;
            |        };
            |
            |        jQuery.ajax = function(options) {
            |            var xhr = new XMLHttpRequest();
            |            var method = (options.type || options.method || 'GET').toUpperCase();
            |            var url = options.url || '';
            |            var async = options.async !== false;
            |            var data = options.data;
            |
            |            xhr.open(method, url, async);
            |            xhr.onreadystatechange = function() {
            |                if (xhr.readyState === 4) {
            |                    if (xhr.status >= 200 && xhr.status < 300) {
            |                        if (options.success) options.success(xhr.responseText, xhr.statusText, xhr);
            |                    } else {
            |                        if (options.error) options.error(xhr, xhr.statusText);
            |                    }
            |                    if (options.complete) options.complete(xhr, xhr.statusText);
            |                }
            |            };
            |            xhr.onerror = function() {
            |                if (options.error) options.error(xhr, 'error');
            |            };
            |            if (options.headers) {
            |                for (var key in options.headers) {
            |                    xhr.setRequestHeader(key, options.headers[key]);
            |                }
            |            }
            |            xhr.send(data);
            |            return xhr;
            |        };
            |
            |        jQuery.get = function(url, data, success, dataType) {
            |            return jQuery.ajax({ url: url, data: data, success: success, dataType: dataType });
            |        };
            |
            |        jQuery.post = function(url, data, success, dataType) {
            |            return jQuery.ajax({ url: url, type: 'POST', data: data, success: success, dataType: dataType });
            |        };
            |
            |        jQuery.ready = function(handler) {
            |            if (document.readyState !== 'loading') {
            |                setTimeout(handler, 0);
            |            } else {
            |                document.addEventListener('DOMContentLoaded', handler);
            |            }
            |        };
            |
            |        // v50 新增: jQuery.cookie() 静态方法
            |        jQuery.cookie = function(name, value, options) {
            |            if (value === undefined) {
            |                var cookies = document.cookie.split(';');
            |                for (var i = 0; i < cookies.length; i++) {
            |                    var cookie = cookies[i].trim();
            |                    if (cookie.indexOf(name + '=') === 0) {
            |                        return decodeURIComponent(cookie.substring(name.length + 1));
            |                    }
            |                }
            |                return null;
            |            } else {
            |                var cookieStr = name + '=' + encodeURIComponent(value);
            |                if (options) {
            |                    if (options.expires) {
            |                        if (typeof options.expires === 'number') {
            |                            var days = options.expires, t = new Date();
            |                            t.setTime(t.getTime() + days * 86400000);
            |                            cookieStr += '; expires=' + t.toUTCString();
            |                        } else {
            |                            cookieStr += '; expires=' + options.expires;
            |                        }
            |                    }
            |                    if (options.path) cookieStr += '; path=' + options.path;
            |                    if (options.domain) cookieStr += '; domain=' + options.domain;
            |                    if (options.secure) cookieStr += '; secure';
            |                }
            |                document.cookie = cookieStr;
            |            }
            |        };
            |
            |        // v50: struts2_jquery 完整支持
            |        jQuery.struts2_jquery = {
            |            version: '3.6.1',
            |            scriptPath: '/eams/static/',
            |            locale: 'zh',
            |            loadAtOnce: true,
            |            min: false
            |        };
            |
            |        // v50: ajaxSettings
            |        jQuery.ajaxSettings = { traditional: true };
            |        jQuery.ajaxSetup = function(options) {
            |            jQuery.extend(jQuery.ajaxSettings, options);
            |        };
            |
            |        // v50: scriptPath
            |        jQuery.scriptPath = '/eams/static/';
            |        jQuery.struts2_jquerySuffix = '';
            |
            |        window.jQuery = jQuery;
            |        window.${'$'} = jQuery;
            |
            |        console.log('[v50 Inject] jQuery 模拟注入成功 (via HTML interception)');
            |    }
            |
            |    // ==================== v50 beangle (bg) 核心模拟（完整版）====================
            |    if (typeof window.bg === 'undefined') {
            |        window.bg = {
            |            contextPath: '/eams',
            |
            |            Go: function(link, targetId, async) {
            |                var url;
            |                if (typeof link === 'string') {
            |                    url = link;
            |                } else if (link && link.href) {
            |                    url = link.href;
            |                } else {
            |                    return true;
            |                }
            |
            |                var target = targetId ? document.getElementById(targetId) : null;
            |
            |                if (target) {
            |                    var xhr = new XMLHttpRequest();
            |                    xhr.open('GET', url, async !== 'false');
            |                    xhr.onload = function() {
            |                        if (xhr.status === 200) {
            |                            target.innerHTML = xhr.responseText;
            |                            var scripts = target.querySelectorAll('script');
            |                            scripts.forEach(function(script) {
            |                                var newScript = document.createElement('script');
            |                                if (script.src) {
            |                                    newScript.src = script.src;
            |                                } else {
            |                                    newScript.textContent = script.textContent;
            |                                }
            |                                document.head.appendChild(newScript);
            |                                document.head.removeChild(newScript);
            |                            });
            |                        }
            |                    };
            |                    xhr.onerror = function() { console.error('[bg.Go] AJAX error:', url); };
            |                    xhr.send();
            |                    return false;
            |                }
            |                return true;
            |            },
            |
            |            ready: function(callback) {
            |                if (document.readyState !== 'loading') {
            |                    setTimeout(callback, 0);
            |                } else {
            |                    document.addEventListener('DOMContentLoaded', callback);
            |                }
            |            },
            |
            |            // v50 新增: form 对象
            |            form: {
            |                submit: function(form, action, target) {
            |                    if (typeof form === 'string') form = document.getElementById(form) || document.forms[form];
            |                    if (!form) return;
            |                    if (action) form.action = action;
            |                    if (target) form.target = target;
            |                    form.submit();
            |                },
            |                addInput: function(form, name, value) {
            |                    if (typeof form === 'string') form = document.getElementById(form);
            |                    if (!form) return;
            |                    var input = document.createElement('input');
            |                    input.type = 'hidden';
            |                    input.name = name;
            |                    input.value = value;
            |                    form.appendChild(input);
            |                }
            |            },
            |
            |            // v50 新增: page 方法
            |            page: function(url, params) {
            |                return {
            |                    _url: url,
            |                    _params: params || '',
            |                    target: function(target, gridId) {
            |                        return this;
            |                    },
            |                    action: function(url) {
            |                        this._url = url;
            |                        return this;
            |                    },
            |                    addParams: function(params) {
            |                        this._params += (this._params ? '&' : '') + params;
            |                        return this;
            |                    },
            |                    orderBy: function(order) {
            |                        return this;
            |                    }
            |                };
            |            },
            |
            |            ui: {
            |                module: {
            |                    moduleClick: function(id) {
            |                        var module = document.getElementById(id);
            |                        if (module) {
            |                            var body = module.querySelector('.modulebody');
            |                            if (body) {
            |                                if (body.style.display === 'none') {
            |                                    body.style.display = '';
            |                                    module.classList.remove('collapsed');
            |                                    module.classList.add('expanded');
            |                                } else {
            |                                    body.style.display = 'none';
            |                                    module.classList.remove('expanded');
            |                                    module.classList.add('collapsed');
            |                                }
            |                            }
            |                        }
            |                    }
            |                }
            |            }
            |        };
            |
            |        window.beangle = window.bg;
            |
            |        console.log('[v50 Inject] beangle (bg) 模拟注入成功 (via HTML interception)');
            |    }
            |
            |    // ==================== v70 underscore.js 核心模拟 ====================
            |    if (typeof window._ === 'undefined') {
            |        window._ = {
            |            // 数组/集合方法
            |            each: function(list, callback, context) {
            |                if (Array.isArray(list)) {
            |                    for (var i = 0; i < list.length; i++) {
            |                        callback.call(context || list[i], list[i], i, list);
            |                    }
            |                } else {
            |                    for (var key in list) {
            |                        if (list.hasOwnProperty(key)) {
            |                            callback.call(context || list[key], list[key], key, list);
            |                        }
            |                    }
            |                }
            |                return list;
            |            },
            |
            |            map: function(list, callback, context) {
            |                var result = [];
            |                if (Array.isArray(list)) {
            |                    for (var i = 0; i < list.length; i++) {
            |                        result.push(callback.call(context, list[i], i, list));
            |                    }
            |                }
            |                return result;
            |            },
            |
            |            filter: function(list, predicate, context) {
            |                var result = [];
            |                if (Array.isArray(list)) {
            |                    for (var i = 0; i < list.length; i++) {
            |                        if (predicate.call(context, list[i], i, list)) {
            |                            result.push(list[i]);
            |                        }
            |                    }
            |                }
            |                return result;
            |            },
            |
            |            reject: function(list, predicate, context) {
            |                var result = [];
            |                if (Array.isArray(list)) {
            |                    for (var i = 0; i < list.length; i++) {
            |                        if (!predicate.call(context, list[i], i, list)) {
            |                            result.push(list[i]);
            |                        }
            |                    }
            |                }
            |                return result;
            |            },
            |
            |            find: function(list, predicate, context) {
            |                if (Array.isArray(list)) {
            |                    for (var i = 0; i < list.length; i++) {
            |                        if (predicate.call(context, list[i], i, list)) {
            |                            return list[i];
            |                        }
            |                    }
            |                }
            |                return undefined;
            |            },
            |
            |            where: function(list, properties) {
            |                return this.filter(list, function(item) {
            |                    for (var key in properties) {
            |                        if (properties.hasOwnProperty(key) && item[key] !== properties[key]) {
            |                            return false;
            |                        }
            |                    }
            |                    return true;
            |                });
            |            },
            |
            |            findWhere: function(list, properties) {
            |                return this.find(list, function(item) {
            |                    for (var key in properties) {
            |                        if (properties.hasOwnProperty(key) && item[key] !== properties[key]) {
            |                            return false;
            |                        }
            |                    }
            |                    return true;
            |                });
            |            },
            |
            |            contains: function(list, value, fromIndex) {
            |                if (Array.isArray(list)) {
            |                    var start = fromIndex || 0;
            |                    for (var i = start; i < list.length; i++) {
            |                        if (list[i] === value) return true;
            |                    }
            |                }
            |                return false;
            |            },
            |
            |            // 对象方法
            |            keys: function(obj) {
            |                var result = [];
            |                for (var key in obj) {
            |                    if (obj.hasOwnProperty(key)) result.push(key);
            |                }
            |                return result;
            |            },
            |
            |            values: function(obj) {
            |                var result = [];
            |                for (var key in obj) {
            |                    if (obj.hasOwnProperty(key)) result.push(obj[key]);
            |                }
            |                return result;
            |            },
            |
            |            extend: function(obj) {
            |                for (var i = 1; i < arguments.length; i++) {
            |                    for (var key in arguments[i]) {
            |                        if (arguments[i].hasOwnProperty(key)) {
            |                            obj[key] = arguments[i][key];
            |                        }
            |                    }
            |                }
            |                return obj;
            |            },
            |
            |            pick: function(obj, keys) {
            |                var result = {};
            |                var keyList = Array.isArray(keys) ? keys : Array.prototype.slice.call(arguments, 1);
            |                for (var i = 0; i < keyList.length; i++) {
            |                    var key = keyList[i];
            |                    if (obj.hasOwnProperty(key)) {
            |                        result[key] = obj[key];
            |                    }
            |                }
            |                return result;
            |            },
            |
            |            // 工具方法
            |            isEmpty: function(obj) {
            |                if (obj == null) return true;
            |                if (Array.isArray(obj) || typeof obj === 'string') return obj.length === 0;
            |                for (var key in obj) {
            |                    if (obj.hasOwnProperty(key)) return false;
            |                }
            |                return true;
            |            },
            |
            |            isArray: function(obj) {
            |                return Array.isArray(obj);
            |            },
            |
            |            isObject: function(obj) {
            |                return obj && typeof obj === 'object';
            |            },
            |
            |            isFunction: function(obj) {
            |                return typeof obj === 'function';
            |            },
            |
            |            isString: function(obj) {
            |                return typeof obj === 'string';
            |            },
            |
            |            isNumber: function(obj) {
            |                return typeof obj === 'number';
            |            },
            |
            |            // 链式调用支持
            |            chain: function(obj) {
            |                var instance = this;
            |                var wrapper = {
            |                    _wrapped: obj,
            |                    value: function() { return this._wrapped; }
            |                };
            |                var methods = ['map', 'filter', 'reject', 'find', 'where', 'sortBy', 'groupBy'];
            |                methods.forEach(function(method) {
            |                    wrapper[method] = function() {
            |                        var args = [this._wrapped].concat(Array.prototype.slice.call(arguments));
            |                        this._wrapped = instance[method].apply(instance, args);
            |                        return this;
            |                    };
            |                });
            |                return wrapper;
            |            }
            |        };
            |
            |        console.log('[v70 Inject] underscore (_) 模拟注入成功 (via HTML interception)');
            |    }
            |
            |    // ==================== v50 CourseTable 和 TaskActivity 模拟 ====================
            |    if (typeof window.CourseTable === 'undefined') {
            |        window.CourseTable = function(year, unitCounts) {
            |            this.year = year;
            |            this.unitCounts = unitCounts || 77;
            |            this.activities = [];
            |            this.marshalContents = [];
            |            for (var i = 0; i < this.unitCounts; i++) {
            |                this.activities[i] = [];
            |            }
            |        };
            |
            |        window.CourseTable.prototype.marshalTable = function(startRow, startCol, maxWeeks) {
            |            // marshalTable 实现
            |        };
            |    }
            |
            |    if (typeof window.TaskActivity === 'undefined') {
            |        window.TaskActivity = function(teacherId, teacherName, courseId, courseName, courseCode, roomId, roomName, vaildWeeks, param8, param9, assistantName, param11) {
            |            this.teacherId = teacherId || '';
            |            this.teacherName = teacherName || '';
            |            this.courseId = courseId || '';
            |            this.courseName = courseName || '';
            |            this.courseCode = courseCode || '';
            |            this.roomId = roomId || '';
            |            this.roomName = roomName || '';
            |            this.vaildWeeks = vaildWeeks || '';
            |            this.param8 = param8;
            |            this.param9 = param9;
            |            this.assistantName = assistantName || '';
            |            this.param11 = param11;
            |        };
            |    }
            |
            |    // 确保 unitCount 存在
            |    if (typeof window.unitCount === 'undefined') {
            |        window.unitCount = 11;
            |    }
            |
            |    // 标记注入完成
            |    window._v50Injected = true;
            |    window._v50InjectionMethod = 'HTML interception';
            |
            |    console.log('[v50 Inject] 脚本注入完成 - jQuery/bg/CourseTable/TaskActivity (via HTML interception)');
            |})();
            |</script>
            |
        """.trimMargin()
    }

    /**
     * 修改 HTML 内容，在 <head> 开头注入脚本
     * @param html 原始 HTML 内容
     * @return 修改后的 HTML 内容
     */
    fun injectIntoHtml(html: String): String {
        val headRegex = Regex("<head[^>]*>", RegexOption.IGNORE_CASE)
        val scriptToInject = getHeadInjectionScript()

        return if (headRegex.containsMatchIn(html)) {
            headRegex.replace(html) { matchResult ->
                matchResult.value + "\n" + scriptToInject
            }
        } else {
            // 如果没有 head 标签，在 html 开头添加
            "<!DOCTYPE html><html><head>$scriptToInject</head><body>" + html + "</body></html>"
        }
    }

    /**
     * [v70] 判断 URL 是否为 HTML 页面请求
     * 只拦截主页面请求，不拦截数据请求和静态资源
     */
    /**
     * [v70] 判断 URL 是否为 HTML 页面请求
     * 只拦截主页面请求，            * 不拦截数据请求和静态资源
     *
            * @param url 请求的 URL
            * @return 是否为 HTML 页面请求
            */
            fun isHtmlRequest(url: String): Boolean {
                val lowerUrl = url.lowercase()

                // 排除静态资源
                if (lowerUrl.contains(".js") || lowerUrl.contains(".css") ||
                    lowerUrl.contains(".png") || lowerUrl.contains(".jpg") ||
                    lowerUrl.contains(".gif") || lowerUrl.contains(".ico") ||
                    lowerUrl.contains(".woff") || lowerUrl.contains(".woff2") ||
                    lowerUrl.contains(".ttf") || lowerUrl.contains(".eot")) {
                    return false
                }

                // 排除数据请求（带感叹号的 action）
                if (lowerUrl.contains("!courseTable.action") ||
                    lowerUrl.contains("!dataquery.action") ||
                    lowerUrl.contains("!search.action") ||
                    lowerUrl.contains("dataquery.action") ||
                    lowerUrl.contains(".htm")) {
                    return false
                }

                // 只拦截主要的 .action 页面（不带感叹号）
                // home.action, courseTableForStd.action 等
                return lowerUrl.contains(".action") && !lowerUrl.contains("!")
            }

    /**
     * [v73 fix3] 获取纯 JavaScript 代码（用于 evaluateJavascript）
     * 用于在 onPageFinished 中直接注入脚本，不依赖 shouldInterceptRequest
     *
     * @return 纯 JavaScript 代码字符串
     */
    fun getPureJavaScript(): String {
        // 从 getHeadInjectionScript() 中提取纯 JS 代码（去掉 <script> 标签）
        val fullScript = getHeadInjectionScript()
        // 移除 |<script> 和 |</script> 以及每行开头的 |
        return fullScript
            .lines()
            .filter { it.trim().isNotEmpty() }
            .joinToString("\n") { line ->
                // 移除行首的 | 和可能的前导空格
                line.trimStart().removePrefix("|")
            }
            .removePrefix("<script>")
            .removeSuffix("</script>")
            .trim()
    }
}
