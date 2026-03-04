package com.example.course_schedule_for_chd_v002.util

/**
 * JavaScript 兼容性注入工具类 (v57)
 *
 * 为系统 WebView 提供 beangle 框架所需的 JavaScript 环境
 *
 * 功能：
 * 1. ES6+ Polyfill（Promise、Object.assign、Array.includes）
 * 2. beangle 框架全局对象初始化（table0、unitCount、TaskActivity）
 * 3. 错误捕获（window.onerror、unhandledrejection）
 * 4. 页面就绪检测（checkCourseDataReady）
 * 5. [v49] jQuery 核心模拟（DOM 查询、事件绑定、AJAX）
 * 6. [v49] beangle (bg) 核心模拟（导航、ready 回调）
 * 7. [v50] 配合 ScriptInjector 使用 shouldInterceptRequest 注入
 * 8. [v51] 改进数据检测逻辑，检测实际课程数据而非空数组
 * 9. [v54] 登录页面检测（基于页面内容而非仅 URL）
 * 10. [v56] 完善 jQuery 模拟（get, prev, next, cookie, Deferred 等）
 * 11. [v56] 完善 beangle 模拟（namespace, require, struts2_jquery 等）
 * 12. [v57] 添加 jQuery 方法（change, empty, submit, select, keydown, keyup, keypress 等）
 * 13. [v57] 完善 struts2_jquery（添加 require 方法）
 * 14. [v57] 在脚本开头立即定义 bg 对象（解决时机问题）
 *
 * 注意：v50 主要使用 ScriptInjector.kt 进行 HTML 拦截注入
 * 此文件保留作为 onPageFinished 后的补充注入
 */
object JsCompatibilityPolyfill {

    private const val TAG = "JsPolyfill"

    /**
     * 获取预注入脚本 (v57)
     * 在页面开始加载前注入，包含 jQuery 和 beangle 核心模拟
     * 解决外部 JavaScript 资源加载失败的问题
     *
     * v57: 添加 jQuery 方法（change, empty, submit, select, keydown, keyup, keypress 等）
     *      完善 struts2_jquery（添加 require 方法）
     *      在脚本开头立即定义 bg 对象（解决时机问题）
     */
    fun getPreInjectionScript(): String {
        return """
            (function() {
                'use strict';

                // ==================== v57 立即定义 bg 对象（备份方案） ====================
                // 解决时机问题：页面内联脚本可能在 onPageStarted 预注入前执行
                if (typeof window.bg === 'undefined') {
                    window.bg = { contextPath: '/eams' };
                    window.beangle = window.bg;
                }

                // ==================== v57 jQuery 核心模拟 ====================
                // 完善的 jQuery 实现，包含页面必需的所有功能

                if (typeof window.jQuery === 'undefined' || typeof window.$ === 'undefined') {
                    var jQuery = function(selector) {
                        return new jQuery.fn.init(selector);
                    };

                    jQuery.fn = jQuery.prototype = {
                        constructor: jQuery,
                        length: 0,
                        jquery: '1.12.4-simulated',

                        init: function(selector) {
                            if (!selector) return this;

                            // 处理字符串选择器
                            if (typeof selector === 'string') {
                                // 处理 HTML 字符串
                                if (selector.charAt(0) === '<' && selector.charAt(selector.length - 1) === '>') {
                                    var temp = document.createElement('div');
                                    temp.innerHTML = selector;
                                    var children = temp.childNodes;
                                    for (var i = 0; i < children.length; i++) {
                                        this[i] = children[i];
                                    }
                                    this.length = children.length;
                                } else {
                                    // CSS 选择器
                                    try {
                                        var elements = document.querySelectorAll(selector);
                                        for (var i = 0; i < elements.length; i++) {
                                            this[i] = elements[i];
                                        }
                                        this.length = elements.length;
                                    } catch (e) {
                                        console.error('[jQuery] Selector error:', e);
                                    }
                                }
                            } else if (selector.nodeType) {
                                // DOM 元素
                                this[0] = selector;
                                this.length = 1;
                            } else if (typeof selector === 'function') {
                                // $(function() {}) -> ready
                                jQuery.ready(selector);
                            } else if (Array.isArray(selector)) {
                                // 数组
                                for (var i = 0; i < selector.length; i++) {
                                    this[i] = selector[i];
                                }
                                this.length = selector.length;
                            }

                            return this;
                        },

                        // [v56] get 方法 - 获取原生 DOM 元素
                        get: function(index) {
                            if (index === undefined) {
                                return Array.prototype.slice.call(this);
                            }
                            return index < 0 ? this[this.length + index] : this[index];
                        },

                        each: function(callback) {
                            for (var i = 0; i < this.length; i++) {
                                if (callback.call(this[i], i, this[i]) === false) break;
                            }
                            return this;
                        },

                        click: function(handler) {
                            return this.on('click', handler);
                        },

                        on: function(event, handler) {
                            return this.each(function() {
                                this.addEventListener(event, handler);
                            });
                        },

                        off: function(event, handler) {
                            return this.each(function() {
                                this.removeEventListener(event, handler);
                            });
                        },

                        ready: function(handler) {
                            if (document.readyState !== 'loading') {
                                setTimeout(handler, 0);
                            } else {
                                document.addEventListener('DOMContentLoaded', handler);
                            }
                            return this;
                        },

                        addClass: function(className) {
                            return this.each(function() {
                                if (this.classList) {
                                    this.classList.add(className);
                                }
                            });
                        },

                        removeClass: function(className) {
                            return this.each(function() {
                                if (this.classList) {
                                    this.classList.remove(className);
                                }
                            });
                        },

                        hasClass: function(className) {
                            for (var i = 0; i < this.length; i++) {
                                if (this[i].classList && this[i].classList.contains(className)) {
                                    return true;
                                }
                            }
                            return false;
                        },

                        css: function(prop, value) {
                            if (typeof prop === 'string' && value === undefined) {
                                return this[0] ? window.getComputedStyle(this[0])[prop] : undefined;
                            }
                            return this.each(function() {
                                if (typeof prop === 'object') {
                                    for (var key in prop) {
                                        this.style[key] = prop[key];
                                    }
                                } else {
                                    this.style[prop] = value;
                                }
                            });
                        },

                        html: function(content) {
                            if (content === undefined) {
                                return this[0] ? this[0].innerHTML : undefined;
                            }
                            return this.each(function() {
                                this.innerHTML = content;
                            });
                        },

                        attr: function(name, value) {
                            if (value === undefined) {
                                return this[0] ? this[0].getAttribute(name) : undefined;
                            }
                            return this.each(function() {
                                this.setAttribute(name, value);
                            });
                        },

                        parent: function() {
                            var result = [];
                            this.each(function() {
                                if (this.parentElement && result.indexOf(this.parentElement) === -1) {
                                    result.push(this.parentElement);
                                }
                            });
                            return jQuery(result);
                        },

                        // [v56] prev 方法 - 获取前一个兄弟元素
                        prev: function() {
                            var result = [];
                            this.each(function() {
                                var prev = this.previousElementSibling;
                                if (prev && result.indexOf(prev) === -1) {
                                    result.push(prev);
                                }
                            });
                            return jQuery(result);
                        },

                        // [v56] next 方法 - 获取后一个兄弟元素
                        next: function() {
                            var result = [];
                            this.each(function() {
                                var next = this.nextElementSibling;
                                if (next && result.indexOf(next) === -1) {
                                    result.push(next);
                                }
                            });
                            return jQuery(result);
                        },

                        // [v56] children 方法 - 获取子元素
                        children: function(selector) {
                            var result = [];
                            this.each(function() {
                                var children = this.children;
                                for (var i = 0; i < children.length; i++) {
                                    if (result.indexOf(children[i]) === -1) {
                                        if (!selector || children[i].matches(selector)) {
                                            result.push(children[i]);
                                        }
                                    }
                                }
                            });
                            return jQuery(result);
                        },

                        // [v56] siblings 方法 - 获取兄弟元素
                        siblings: function() {
                            var result = [];
                            this.each(function() {
                                var siblings = this.parentElement ? this.parentElement.children : [];
                                for (var i = 0; i < siblings.length; i++) {
                                    if (siblings[i] !== this && result.indexOf(siblings[i]) === -1) {
                                        result.push(siblings[i]);
                                    }
                                }
                            });
                            return jQuery(result);
                        },

                        find: function(selector) {
                            var result = [];
                            this.each(function() {
                                var found = this.querySelectorAll(selector);
                                for (var i = 0; i < found.length; i++) {
                                    if (result.indexOf(found[i]) === -1) {
                                        result.push(found[i]);
                                    }
                                }
                            });
                            return jQuery(result);
                        },

                        // [v56] filter 方法 - 过滤元素
                        filter: function(selector) {
                            var result = [];
                            if (typeof selector === 'function') {
                                this.each(function(i) {
                                    if (selector.call(this, i, this)) {
                                        result.push(this);
                                    }
                                });
                            } else {
                                this.each(function() {
                                    if (this.matches && this.matches(selector)) {
                                        result.push(this);
                                    }
                                });
                            }
                            return jQuery(result);
                        },

                        // [v56] is 方法 - 检查是否匹配选择器
                        is: function(selector) {
                            for (var i = 0; i < this.length; i++) {
                                if (this[i].matches && this[i].matches(selector)) {
                                    return true;
                                }
                            }
                            return false;
                        },

                        eq: function(index) {
                            return jQuery(this[index >= 0 ? index : this.length + index]);
                        },

                        first: function() {
                            return jQuery(this[0]);
                        },

                        last: function() {
                            return jQuery(this[this.length - 1]);
                        },

                        // [v56] index 方法 - 获取元素在父元素中的索引
                        index: function() {
                            if (this.length === 0) return -1;
                            var parent = this[0].parentElement;
                            if (!parent) return -1;
                            return Array.prototype.indexOf.call(parent.children, this[0]);
                        },

                        val: function(value) {
                            if (value === undefined) {
                                return this[0] ? this[0].value : undefined;
                            }
                            return this.each(function() {
                                this.value = value;
                            });
                        },

                        prop: function(name, value) {
                            if (value === undefined) {
                                return this[0] ? this[0][name] : undefined;
                            }
                            return this.each(function() {
                                this[name] = value;
                            });
                        },

                        // [v56] remove 方法 - 移除元素
                        remove: function() {
                            return this.each(function() {
                                if (this.parentElement) {
                                    this.parentElement.removeChild(this);
                                }
                            });
                        },

                        // [v56] append 方法 - 在末尾添加内容
                        append: function(content) {
                            return this.each(function() {
                                if (typeof content === 'string') {
                                    this.insertAdjacentHTML('beforeend', content);
                                } else if (content.nodeType) {
                                    this.appendChild(content);
                                } else if (content.jquery || content.length) {
                                    var self = this;
                                    content.each(function() {
                                        self.appendChild(this.cloneNode(true));
                                    });
                                }
                            });
                        },

                        // [v56] prepend 方法 - 在开头添加内容
                        prepend: function(content) {
                            return this.each(function() {
                                if (typeof content === 'string') {
                                    this.insertAdjacentHTML('afterbegin', content);
                                } else if (content.nodeType) {
                                    this.insertBefore(content, this.firstChild);
                                }
                            });
                        },

                        // [v56] before 方法 - 在元素前添加内容
                        before: function(content) {
                            return this.each(function() {
                                if (typeof content === 'string') {
                                    this.insertAdjacentHTML('beforebegin', content);
                                } else if (content.nodeType && this.parentElement) {
                                    this.parentElement.insertBefore(content, this);
                                }
                            });
                        },

                        // [v56] after 方法 - 在元素后添加内容
                        after: function(content) {
                            return this.each(function() {
                                if (typeof content === 'string') {
                                    this.insertAdjacentHTML('afterend', content);
                                } else if (content.nodeType && this.parentElement) {
                                    this.parentElement.insertBefore(content, this.nextSibling);
                                }
                            });
                        },

                        hide: function() {
                            return this.css('display', 'none');
                        },

                        show: function() {
                            return this.css('display', '');
                        },

                        toggle: function() {
                            return this.each(function() {
                                if (this.style.display === 'none') {
                                    this.style.display = '';
                                } else {
                                    this.style.display = 'none';
                                }
                            });
                        },

                        // [v57] focus 方法 - 支持事件绑定和触发
                        focus: function(handler) {
                            if (handler === undefined) {
                                if (this[0] && this[0].focus) {
                                    this[0].focus();
                                }
                                return this;
                            }
                            return this.on('focus', handler);
                        },

                        // [v57] blur 方法 - 支持事件绑定和触发
                        blur: function(handler) {
                            if (handler === undefined) {
                                if (this[0] && this[0].blur) {
                                    this[0].blur();
                                }
                                return this;
                            }
                            return this.on('blur', handler);
                        },

                        // [v56] text 方法 - 获取/设置文本内容
                        text: function(content) {
                            if (content === undefined) {
                                return this[0] ? this[0].textContent : undefined;
                            }
                            return this.each(function() {
                                this.textContent = content;
                            });
                        },

                        // [v56] data 方法 - 获取/设置数据
                        data: function(key, value) {
                            if (value === undefined) {
                                if (this[0] && this[0].dataset) {
                                    return this[0].dataset[key];
                                }
                                return undefined;
                            }
                            return this.each(function() {
                                if (this.dataset) {
                                    this.dataset[key] = value;
                                }
                            });
                        },

                        // [v56] trigger 方法 - 触发事件
                        trigger: function(eventName) {
                            return this.each(function() {
                                var event;
                                if (typeof Event === 'function') {
                                    event = new Event(eventName, { bubbles: true, cancelable: true });
                                } else {
                                    event = document.createEvent('Event');
                                    event.initEvent(eventName, true, true);
                                }
                                this.dispatchEvent(event);
                            });
                        },

                        // ==================== v57 新增方法 ====================

                        // [v57] change 方法 - change 事件绑定
                        change: function(handler) {
                            if (handler === undefined) {
                                return this.trigger('change');
                            }
                            return this.on('change', handler);
                        },

                        // [v57] empty 方法 - 清空元素内容
                        empty: function() {
                            return this.each(function() {
                                this.innerHTML = '';
                            });
                        },

                        // [v57] submit 方法 - 表单提交
                        submit: function(handler) {
                            if (handler === undefined) {
                                return this.trigger('submit');
                            }
                            return this.on('submit', handler);
                        },

                        // [v57] select 方法 - 选择事件
                        select: function(handler) {
                            if (handler === undefined) {
                                return this.trigger('select');
                            }
                            return this.on('select', handler);
                        },

                        // [v57] keydown 方法
                        keydown: function(handler) {
                            return this.on('keydown', handler);
                        },

                        // [v57] keyup 方法
                        keyup: function(handler) {
                            return this.on('keyup', handler);
                        },

                        // [v57] keypress 方法
                        keypress: function(handler) {
                            return this.on('keypress', handler);
                        },

                        // [v57] mousedown 方法
                        mousedown: function(handler) {
                            return this.on('mousedown', handler);
                        },

                        // [v57] mouseup 方法
                        mouseup: function(handler) {
                            return this.on('mouseup', handler);
                        },

                        // [v57] mouseover 方法
                        mouseover: function(handler) {
                            return this.on('mouseover', handler);
                        },

                        // [v57] mouseout 方法
                        mouseout: function(handler) {
                            return this.on('mouseout', handler);
                        },

                        // [v57] mouseenter 方法
                        mouseenter: function(handler) {
                            return this.on('mouseenter', handler);
                        },

                        // [v57] mouseleave 方法
                        mouseleave: function(handler) {
                            return this.on('mouseleave', handler);
                        },

                        // [v57] resize 方法
                        resize: function(handler) {
                            return this.on('resize', handler);
                        },

                        // [v57] scroll 方法
                        scroll: function(handler) {
                            return this.on('scroll', handler);
                        },

                        // [v57] serialize 方法 - 表单序列化
                        serialize: function() {
                            var result = [];
                            this.find('input, select, textarea').each(function() {
                                if (this.name && !this.disabled) {
                                    if (this.type === 'checkbox' || this.type === 'radio') {
                                        if (this.checked) {
                                            result.push(encodeURIComponent(this.name) + '=' + encodeURIComponent(this.value));
                                        }
                                    } else {
                                        result.push(encodeURIComponent(this.name) + '=' + encodeURIComponent(this.value));
                                    }
                                }
                            });
                            return result.join('&');
                        },

                        // [v57] serializeArray 方法
                        serializeArray: function() {
                            var result = [];
                            this.find('input, select, textarea').each(function() {
                                if (this.name && !this.disabled) {
                                    if (this.type === 'checkbox' || this.type === 'radio') {
                                        if (this.checked) {
                                            result.push({ name: this.name, value: this.value });
                                        }
                                    } else {
                                        result.push({ name: this.name, value: this.value });
                                    }
                                }
                            });
                            return result;
                        },

                        // [v57] prop 方法 - 获取/设置属性
                        prop: function(name, value) {
                            if (value === undefined) {
                                return this[0] ? this[0][name] : undefined;
                            }
                            return this.each(function() {
                                this[name] = value;
                            });
                        },

                        // [v57] removeAttr 方法 - 移除属性
                        removeAttr: function(name) {
                            return this.each(function() {
                                this.removeAttribute(name);
                            });
                        },

                        // [v57] removeProp 方法 - 移除属性
                        removeProp: function(name) {
                            return this.each(function() {
                                delete this[name];
                            });
                        },

                        // [v57] clone 方法 - 克隆元素
                        clone: function(deep) {
                            var result = [];
                            this.each(function() {
                                result.push(this.cloneNode(deep !== false));
                            });
                            return jQuery(result);
                        },

                        // [v57] wrap 方法 - 包装元素
                        wrap: function(wrapper) {
                            return this.each(function() {
                                if (typeof wrapper === 'string') {
                                    var temp = document.createElement('div');
                                    temp.innerHTML = wrapper;
                                    var wrapEl = temp.firstChild;
                                    this.parentNode.insertBefore(wrapEl, this);
                                    wrapEl.appendChild(this);
                                }
                            });
                        },

                        // [v57] unwrap 方法 - 移除包装
                        unwrap: function() {
                            return this.each(function() {
                                var parent = this.parentNode;
                                if (parent && parent !== document.body) {
                                    while (parent.firstChild) {
                                        parent.parentNode.insertBefore(parent.firstChild, parent);
                                    }
                                    parent.parentNode.removeChild(parent);
                                }
                            });
                        },

                        // [v57] replaceWith 方法 - 替换元素
                        replaceWith: function(content) {
                            return this.each(function() {
                                if (typeof content === 'string') {
                                    this.insertAdjacentHTML('beforebegin', content);
                                } else if (content.nodeType) {
                                    this.parentNode.insertBefore(content, this);
                                }
                                this.parentNode.removeChild(this);
                            });
                        },

                        // [v57] offset 方法 - 获取元素偏移
                        offset: function() {
                            if (this.length === 0) return null;
                            var rect = this[0].getBoundingClientRect();
                            return {
                                top: rect.top + window.pageYOffset,
                                left: rect.left + window.pageXOffset,
                                width: rect.width,
                                height: rect.height
                            };
                        },

                        // [v57] position 方法 - 获取相对父元素位置
                        position: function() {
                            if (this.length === 0) return null;
                            return {
                                top: this[0].offsetTop,
                                left: this[0].offsetLeft
                            };
                        },

                        // [v57] width 方法
                        width: function(value) {
                            if (value === undefined) {
                                if (this[0] === window) return window.innerWidth;
                                if (this[0] === document) return document.documentElement.scrollWidth;
                                return this[0] ? this[0].offsetWidth : null;
                            }
                            return this.each(function() {
                                this.style.width = typeof value === 'number' ? value + 'px' : value;
                            });
                        },

                        // [v57] height 方法
                        height: function(value) {
                            if (value === undefined) {
                                if (this[0] === window) return window.innerHeight;
                                if (this[0] === document) return document.documentElement.scrollHeight;
                                return this[0] ? this[0].offsetHeight : null;
                            }
                            return this.each(function() {
                                this.style.height = typeof value === 'number' ? value + 'px' : value;
                            });
                        },

                        // [v57] innerWidth 方法
                        innerWidth: function() {
                            if (this.length === 0) return null;
                            var el = this[0];
                            return el.clientWidth;
                        },

                        // [v57] innerHeight 方法
                        innerHeight: function() {
                            if (this.length === 0) return null;
                            var el = this[0];
                            return el.clientHeight;
                        },

                        // [v57] outerWidth 方法
                        outerWidth: function(includeMargin) {
                            if (this.length === 0) return null;
                            var el = this[0];
                            var width = el.offsetWidth;
                            if (includeMargin) {
                                var style = window.getComputedStyle(el);
                                width += parseFloat(style.marginLeft) + parseFloat(style.marginRight);
                            }
                            return width;
                        },

                        // [v57] outerHeight 方法
                        outerHeight: function(includeMargin) {
                            if (this.length === 0) return null;
                            var el = this[0];
                            var height = el.offsetHeight;
                            if (includeMargin) {
                                var style = window.getComputedStyle(el);
                                height += parseFloat(style.marginTop) + parseFloat(style.marginBottom);
                            }
                            return height;
                        },

                        // [v57] scrollLeft 方法
                        scrollLeft: function(value) {
                            if (value === undefined) {
                                if (this[0] === window || this[0] === document) {
                                    return window.pageXOffset || document.documentElement.scrollLeft;
                                }
                                return this[0] ? this[0].scrollLeft : null;
                            }
                            return this.each(function() {
                                this.scrollLeft = value;
                            });
                        },

                        // [v57] scrollTop 方法
                        scrollTop: function(value) {
                            if (value === undefined) {
                                if (this[0] === window || this[0] === document) {
                                    return window.pageYOffset || document.documentElement.scrollTop;
                                }
                                return this[0] ? this[0].scrollTop : null;
                            }
                            return this.each(function() {
                                this.scrollTop = value;
                            });
                        }
                    };

                    jQuery.fn.init.prototype = jQuery.fn;

                    // ==================== jQuery 静态方法 ====================

                    jQuery.each = function(obj, callback) {
                        if (Array.isArray(obj)) {
                            for (var i = 0; i < obj.length; i++) {
                                if (callback.call(obj[i], i, obj[i]) === false) break;
                            }
                        } else {
                            for (var key in obj) {
                                if (callback.call(obj[key], key, obj[key]) === false) break;
                            }
                        }
                    };

                    jQuery.extend = function() {
                        var target = arguments[0] || {};
                        for (var i = 1; i < arguments.length; i++) {
                            for (var key in arguments[i]) {
                                target[key] = arguments[i][key];
                            }
                        }
                        return target;
                    };

                    jQuery.ajax = function(options) {
                        var xhr = new XMLHttpRequest();
                        var method = (options.type || options.method || 'GET').toUpperCase();
                        var url = options.url || '';
                        var async = options.async !== false;
                        var data = options.data;

                        xhr.open(method, url, async);
                        xhr.onreadystatechange = function() {
                            if (xhr.readyState === 4) {
                                if (xhr.status >= 200 && xhr.status < 300) {
                                    if (options.success) {
                                        options.success(xhr.responseText, xhr.statusText, xhr);
                                    }
                                } else {
                                    if (options.error) {
                                        options.error(xhr, xhr.statusText);
                                    }
                                }
                                if (options.complete) {
                                    options.complete(xhr, xhr.statusText);
                                }
                            }
                        };
                        xhr.onerror = function() {
                            if (options.error) {
                                options.error(xhr, 'error');
                            }
                        };

                        if (options.headers) {
                            for (var key in options.headers) {
                                xhr.setRequestHeader(key, options.headers[key]);
                            }
                        }

                        xhr.send(data);
                        return xhr;
                    };

                    jQuery.get = function(url, data, success, dataType) {
                        return jQuery.ajax({ url: url, data: data, success: success, dataType: dataType });
                    };

                    jQuery.post = function(url, data, success, dataType) {
                        return jQuery.ajax({ url: url, type: 'POST', data: data, success: success, dataType: dataType });
                    };

                    jQuery.ready = function(handler) {
                        if (document.readyState !== 'loading') {
                            setTimeout(handler, 0);
                        } else {
                            document.addEventListener('DOMContentLoaded', handler);
                        }
                    };

                    // [v56] cookie 方法
                    jQuery.cookie = function(name, value, options) {
                        if (value === undefined) {
                            // 读取 cookie
                            var cookies = document.cookie.split(';');
                            for (var i = 0; i < cookies.length; i++) {
                                var cookie = cookies[i].trim();
                                if (cookie.indexOf(name + '=') === 0) {
                                    return decodeURIComponent(cookie.substring(name.length + 1));
                                }
                            }
                            return null;
                        } else if (value === null) {
                            // 删除 cookie
                            document.cookie = name + '=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/';
                        } else {
                            // 设置 cookie
                            var cookieStr = name + '=' + encodeURIComponent(value);
                            if (options && options.expires) {
                                var date = new Date();
                                date.setTime(date.getTime() + (options.expires * 24 * 60 * 60 * 1000));
                                cookieStr += '; expires=' + date.toUTCString();
                            }
                            if (options && options.path) {
                                cookieStr += '; path=' + options.path;
                            } else {
                                cookieStr += '; path=/';
                            }
                            if (options && options.domain) {
                                cookieStr += '; domain=' + options.domain;
                            }
                            if (options && options.secure) {
                                cookieStr += '; secure';
                            }
                            document.cookie = cookieStr;
                        }
                    };

                    // [v56] proxy 方法
                    jQuery.proxy = function(fn, context) {
                        return function() {
                            return fn.apply(context, arguments);
                        };
                    };

                    // [v56] isFunction 方法
                    jQuery.isFunction = function(obj) {
                        return typeof obj === 'function';
                    };

                    // [v56] isArray 方法
                    jQuery.isArray = function(obj) {
                        return Array.isArray(obj);
                    };

                    // [v56] isPlainObject 方法
                    jQuery.isPlainObject = function(obj) {
                        return typeof obj === 'object' && obj !== null && obj.constructor === Object;
                    };

                    // [v56] trim 方法
                    jQuery.trim = function(str) {
                        return str ? str.trim() : '';
                    };

                    // [v56] type 方法
                    jQuery.type = function(obj) {
                        if (obj === null) return 'null';
                        if (obj === undefined) return 'undefined';
                        var type = typeof obj;
                        if (type === 'object') {
                            if (Array.isArray(obj)) return 'array';
                            if (obj instanceof Date) return 'date';
                            if (obj instanceof RegExp) return 'regexp';
                        }
                        return type;
                    };

                    // [v56] noop 方法
                    jQuery.noop = function() {};

                    // [v56] when 方法 (简化版)
                    jQuery.when = function(deferreds) {
                        return Promise.resolve(deferreds);
                    };

                    // [v56] Deferred (简化版)
                    jQuery.Deferred = function() {
                        var callbacks = { done: [], fail: [], progress: [] };
                        var state = 'pending';
                        var self = this;

                        return {
                            done: function(fn) { callbacks.done.push(fn); return this; },
                            fail: function(fn) { callbacks.fail.push(fn); return this; },
                            progress: function(fn) { callbacks.progress.push(fn); return this; },
                            resolve: function() {
                                state = 'resolved';
                                var args = arguments;
                                callbacks.done.forEach(function(fn) { fn.apply(null, args); });
                            },
                            reject: function() {
                                state = 'rejected';
                                var args = arguments;
                                callbacks.fail.forEach(function(fn) { fn.apply(null, args); });
                            },
                            notify: function() {
                                var args = arguments;
                                callbacks.progress.forEach(function(fn) { fn.apply(null, args); });
                            },
                            state: function() { return state; },
                            promise: function() {
                                return {
                                    done: this.done,
                                    fail: this.fail,
                                    progress: this.progress,
                                    state: this.state,
                                    then: function(doneFn, failFn) {
                                        if (doneFn) this.done(doneFn);
                                        if (failFn) this.fail(failFn);
                                        return this;
                                    }
                                };
                            }
                        };
                    };

                    // [v57] struts2_jquery 兼容（完整版）
                    jQuery.struts2_jquery = {
                        version: '3.6.1',
                        scriptCache: {},
                        debug: false,

                        // [v57] require 方法 - 加载脚本并执行回调
                        require: function(modules, callback) {
                            var self = this;
                            if (typeof modules === 'string') {
                                modules = [modules];
                            }

                            var loaded = 0;
                            var total = modules.length;

                            if (total === 0) {
                                if (callback) callback();
                                return;
                            }

                            modules.forEach(function(module) {
                                // 检查是否已加载
                                if (self.scriptCache[module]) {
                                    loaded++;
                                    if (loaded === total && callback) {
                                        callback();
                                    }
                                    return;
                                }

                                // 加载脚本
                                var script = document.createElement('script');
                                script.src = module;
                                script.onload = function() {
                                    self.scriptCache[module] = true;
                                    loaded++;
                                    if (loaded === total && callback) {
                                        callback();
                                    }
                                };
                                script.onerror = function() {
                                    console.error('[struts2_jquery] Failed to load: ' + module);
                                    loaded++;
                                    if (loaded === total && callback) {
                                        callback();
                                    }
                                };
                                document.head.appendChild(script);
                            });
                        },

                        // loadScript 方法（保持兼容）
                        loadScript: function(url, callback) {
                            if (this.scriptCache[url]) {
                                if (callback) callback();
                                return;
                            }
                            var script = document.createElement('script');
                            script.src = url;
                            var self = this;
                            script.onload = function() {
                                self.scriptCache[url] = true;
                                if (callback) callback();
                            };
                            script.onerror = function() {
                                if (callback) callback();
                            };
                            document.head.appendChild(script);
                        },

                        // [v57] log 方法
                        log: function(message) {
                            if (this.debug && window.console) {
                                console.log('[struts2_jquery] ' + message);
                            }
                        }
                    };

                    // 注册全局
                    window.jQuery = jQuery;
                    window.$ = jQuery;

                    console.log('[PreInject v57] jQuery 模拟注入成功');
                }

                // ==================== v57 beangle (bg) 核心模拟 ====================

                if (typeof window.bg === 'undefined') {
                    window.bg = {
                        contextPath: '/eams',

                        // 页面导航（AJAX 加载到指定容器）
                        Go: function(link, targetId, async) {
                            var url;
                            if (typeof link === 'string') {
                                url = link;
                            } else if (link && link.href) {
                                url = link.href;
                            } else {
                                return true;
                            }

                            var target = targetId ? document.getElementById(targetId) : null;

                            if (target) {
                                // AJAX 加载内容
                                var xhr = new XMLHttpRequest();
                                xhr.open('GET', url, async !== 'false');
                                xhr.onload = function() {
                                    if (xhr.status === 200) {
                                        target.innerHTML = xhr.responseText;

                                        // 执行新加载的脚本
                                        var scripts = target.querySelectorAll('script');
                                        scripts.forEach(function(script) {
                                            var newScript = document.createElement('script');
                                            if (script.src) {
                                                newScript.src = script.src;
                                            } else {
                                                newScript.textContent = script.textContent;
                                            }
                                            document.head.appendChild(newScript);
                                            document.head.removeChild(newScript);
                                        });
                                    }
                                };
                                xhr.onerror = function() {
                                    console.error('[bg.Go] AJAX error:', url);
                                };
                                xhr.send();
                                return false;
                            }

                            return true;
                        },

                        // DOM ready 回调
                        ready: function(callback) {
                            if (document.readyState !== 'loading') {
                                setTimeout(callback, 0);
                            } else {
                                document.addEventListener('DOMContentLoaded', callback);
                            }
                        },

                        // [v56] namespace 方法 - 创建命名空间
                        namespace: function(ns) {
                            var parts = ns.split('.');
                            var current = window;
                            for (var i = 0; i < parts.length; i++) {
                                if (!current[parts[i]]) {
                                    current[parts[i]] = {};
                                }
                                current = current[parts[i]];
                            }
                            return current;
                        },

                        // [v56] require 方法 (简化版)
                        require: function(moduleName, callback) {
                            if (callback) {
                                setTimeout(function() {
                                    callback(window[moduleName] || {});
                                }, 0);
                            }
                        },

                        // [v56] defer 方法
                        defer: function() {
                            return window.jQuery ? window.jQuery.Deferred() : null;
                        },

                        // [v56] ajax 方法
                        ajax: function(options) {
                            return window.jQuery ? window.jQuery.ajax(options) : null;
                        },

                        // UI 组件
                        ui: {
                            module: {
                                moduleClick: function(id) {
                                    var module = document.getElementById(id);
                                    if (module) {
                                        var body = module.querySelector('.modulebody');
                                        if (body) {
                                            if (body.style.display === 'none') {
                                                body.style.display = '';
                                                module.classList.remove('collapsed');
                                                module.classList.add('expanded');
                                            } else {
                                                body.style.display = 'none';
                                                module.classList.remove('expanded');
                                                module.classList.add('collapsed');
                                            }
                                        }
                                    }
                                }
                            },
                            // [v56] struts2_jquery 兼容
                            struts2_jquery: {
                                version: '3.6.1',
                                scriptCache: {},
                                loadScript: function(url, callback) {
                                    if (this.scriptCache[url]) {
                                        if (callback) callback();
                                        return;
                                    }
                                    var script = document.createElement('script');
                                    script.src = url;
                                    var self = this;
                                    script.onload = function() {
                                        self.scriptCache[url] = true;
                                        if (callback) callback();
                                    };
                                    document.head.appendChild(script);
                                }
                            }
                        },

                        // [v56] util 工具
                        util: {
                            extend: function() {
                                return window.jQuery ? window.jQuery.extend.apply(null, arguments) : null;
                            },
                            each: function(obj, callback) {
                                return window.jQuery ? window.jQuery.each(obj, callback) : null;
                            }
                        }
                    };

                    // beangle 全局对象别名
                    window.beangle = window.bg;

                    // [v56] 确保 struts2_jquery 全局命名空间存在
                    if (typeof window.struts2_jquery === 'undefined') {
                        window.struts2_jquery = window.bg.ui.struts2_jquery;
                    }

                    console.log('[PreInject v57] beangle (bg) 模拟注入成功');
                }

                // 标记预注入完成
                window._preInjected = true;
                window._preInjectedVersion = 'v57';

            })();
        """.trimIndent()
    }

    /**
     * 获取完整的注入脚本
     * 包含所有 polyfill 和 beangle 框架支持
     */
    fun getInjectionScript(): String {
        return """
            (function() {
                'use strict';

                // ==================== ES6+ Polyfills ====================

                // Promise polyfill (简化版)
                if (typeof Promise === 'undefined') {
                    window.Promise = function(executor) {
                        var self = this;
                        self._state = 'pending';
                        self._value = undefined;
                        self._callbacks = [];

                        function resolve(value) {
                            if (self._state === 'pending') {
                                self._state = 'fulfilled';
                                self._value = value;
                                self._callbacks.forEach(function(cb) { cb.onFulfilled(value); });
                            }
                        }

                        function reject(reason) {
                            if (self._state === 'pending') {
                                self._state = 'rejected';
                                self._value = reason;
                                self._callbacks.forEach(function(cb) { cb.onRejected(reason); });
                            }
                        }

                        try {
                            executor(resolve, reject);
                        } catch (e) {
                            reject(e);
                        }
                    };
                }

                // Object.assign polyfill
                if (typeof Object.assign !== 'function') {
                    Object.assign = function(target) {
                        if (target == null) {
                            throw new TypeError('Cannot convert undefined or null to object');
                        }
                        var to = Object(target);
                        for (var i = 1; i < arguments.length; i++) {
                            var nextSource = arguments[i];
                            if (nextSource != null) {
                                for (var key in nextSource) {
                                    if (Object.prototype.hasOwnProperty.call(nextSource, key)) {
                                        to[key] = nextSource[key];
                                    }
                                }
                            }
                        }
                        return to;
                    };
                }

                // Array.prototype.includes polyfill
                if (!Array.prototype.includes) {
                    Array.prototype.includes = function(searchElement) {
                        return this.indexOf(searchElement) !== -1;
                    };
                }

                // String.prototype.includes polyfill
                if (!String.prototype.includes) {
                    String.prototype.includes = function(search, start) {
                        if (typeof start !== 'number') {
                            start = 0;
                        }
                        return this.indexOf(search, start) !== -1;
                    };
                }

                // ==================== beangle 框架支持 ====================

                // 确保 unitCount 存在 (每天11节课)
                if (typeof window.unitCount === 'undefined') {
                    window.unitCount = 11;
                }

                // 确保 table0 存在
                if (typeof window.table0 === 'undefined') {
                    window.table0 = {
                        activities: []
                    };
                }

                // 确保 table0.activities 是数组
                if (!Array.isArray(window.table0.activities)) {
                    window.table0.activities = [];
                }

                // 确保 TaskActivity 构造函数存在
                if (typeof window.TaskActivity === 'undefined') {
                    window.TaskActivity = function(teacherId, teacherName, courseId, courseName, courseCode, roomId, roomName, weeksBitmap, param8, param9, assistantName, param11) {
                        this.teacherId = teacherId || '';
                        this.teacherName = teacherName || '';
                        this.courseId = courseId || '';
                        this.courseName = courseName || '';
                        this.courseCode = courseCode || '';
                        this.roomId = roomId || '';
                        this.roomName = roomName || '';
                        this.weeksBitmap = weeksBitmap || '';
                        this.param8 = param8;
                        this.param9 = param9;
                        this.assistantName = assistantName || '';
                        this.param11 = param11;
                    };
                }

                // 确保数组扩展方法存在
                if (!Array.prototype.extend) {
                    Array.prototype.extend = function(arr) {
                        if (Array.isArray(arr)) {
                            this.push.apply(this, arr);
                        }
                    };
                }

                // ==================== 错误捕获 ====================

                // 初始化错误收集
                window.jsErrors = [];

                // 全局错误捕获
                window.onerror = function(message, source, lineno, colno, error) {
                    var errorInfo = {
                        message: message ? String(message) : '',
                        source: source ? String(source) : '',
                        line: lineno || 0,
                        column: colno || 0,
                        timestamp: new Date().toISOString()
                    };
                    window.jsErrors.push(errorInfo);

                    // 发送到 Android (如果接口存在)
                    if (typeof AndroidLogger !== 'undefined') {
                        try {
                            AndroidLogger.onError(message, source || '', lineno || 0);
                        } catch (e) {
                            console.error('AndroidLogger.onError failed:', e);
                        }
                    }

                    return false;
                };

                // 捕获 Promise 拒绝
                window.addEventListener('unhandledrejection', function(event) {
                    var errorInfo = {
                        message: event.reason ? String(event.reason) : 'Unknown promise rejection',
                        type: 'unhandledrejection',
                        timestamp: new Date().toISOString()
                    };
                    window.jsErrors.push(errorInfo);

                    if (typeof AndroidLogger !== 'undefined') {
                        try {
                            AndroidLogger.onError('Promise rejection: ' + event.reason, '', 0);
                        } catch (e) {
                            console.error('AndroidLogger.onError failed:', e);
                        }
                    }
                });

                // ==================== 页面就绪检测 (v51) ====================

                // [v51] 获取实际课程数据数量
                window.getActivitiesDataCount = function() {
                    var count = 0;
                    if (window.table0 && window.table0.activities && Array.isArray(window.table0.activities)) {
                        for (var i = 0; i < window.table0.activities.length; i++) {
                            if (window.table0.activities[i] && window.table0.activities[i].length > 0) {
                                count += window.table0.activities[i].length;
                            }
                        }
                    }
                    return count;
                };

                // [v51] 课表数据加载检测函数 - 改进版
                window.checkCourseDataReady = function() {
                    try {
                        // 1. 检测 table0 是否存在
                        var hasTable0 = typeof window.table0 !== 'undefined';
                        if (!hasTable0) return false;

                        // 2. [v51 改进] 检测 activities 是否有实际数据（至少有一个非空数组）
                        var hasActivities = false;
                        if (window.table0.activities && Array.isArray(window.table0.activities)) {
                            for (var i = 0; i < window.table0.activities.length; i++) {
                                if (window.table0.activities[i] && window.table0.activities[i].length > 0) {
                                    hasActivities = true;
                                    break;
                                }
                            }
                        }

                        // 3. 检测 courseName 变量
                        var hasCourseName = typeof window.courseName !== 'undefined' && window.courseName;

                        // 4. 检测 DOM 渲染
                        var infoTitleCells = document.querySelectorAll('td.infoTitle');
                        var hasRenderedCells = infoTitleCells.length > 0;

                        // 5. [v51 改进] 检测是否有 TaskActivity 创建代码（检查脚本是否已执行）
                        var hasTaskActivityData = false;
                        var scripts = document.querySelectorAll('script');
                        for (var i = 0; i < scripts.length; i++) {
                            var content = scripts[i].textContent || '';
                            if (content.indexOf('new TaskActivity') !== -1 && content.indexOf('table0.activities') !== -1) {
                                hasTaskActivityData = true;
                                break;
                            }
                        }

                        // [v51] 只要有实际数据就认为就绪
                        var isReady = hasActivities || (hasCourseName && hasRenderedCells) || hasTaskActivityData;

                        return isReady;
                    } catch (e) {
                        console.error('checkCourseDataReady error:', e);
                        return false;
                    }
                };

                // 页面类型检测函数
                window.detectPageType = function() {
                    var url = window.location.href;
                    var result = {
                        url: url,
                        type: 'unknown',
                        hasTable0: typeof window.table0 !== 'undefined',
                        hasActivities: false,
                        hasCourseName: typeof window.courseName !== 'undefined',
                        infoTitleCount: document.querySelectorAll('td.infoTitle').length,
                        jsErrorCount: window.jsErrors ? window.jsErrors.length : 0
                    };

                    if (window.table0 && window.table0.activities) {
                        result.hasActivities = window.table0.activities.length > 0;
                    }

                    // 判断页面类型
                    if (url.indexOf('ids.chd.edu.cn') !== -1) {
                        result.type = 'cas_login';
                    } else if (url.indexOf('courseTableForStd') !== -1) {
                        result.type = 'course_table';
                    } else if (url.indexOf('bkjw.chd.edu.cn') !== -1 && url.indexOf('home.action') !== -1) {
                        result.type = 'eams_home';
                    } else if (url.indexOf('bkjw.chd.edu.cn') !== -1) {
                        result.type = 'eams_other';
                    }

                    return result;
                };

                // 控制台日志增强
                (function() {
                    var originalConsoleLog = console.log;
                    var originalConsoleError = console.error;
                    var originalConsoleWarn = console.warn;

                    console.log = function() {
                        if (typeof AndroidLogger !== 'undefined') {
                            try {
                                AndroidLogger.onLog(Array.prototype.slice.call(arguments).join(' '));
                            } catch (e) {}
                        }
                        originalConsoleLog.apply(console, arguments);
                    };

                    console.error = function() {
                        if (typeof AndroidLogger !== 'undefined') {
                            try {
                                AndroidLogger.onError(Array.prototype.slice.call(arguments).join(' '), '', 0);
                            } catch (e) {}
                        }
                        originalConsoleError.apply(console, arguments);
                    };

                    console.warn = function() {
                        if (typeof AndroidLogger !== 'undefined') {
                            try {
                                AndroidLogger.onLog('[WARN] ' + Array.prototype.slice.call(arguments).join(' '));
                            } catch (e) {}
                        }
                        originalConsoleWarn.apply(console, arguments);
                    };
                })();

                // 标记注入完成
                window._polyfillInjected = true;
                window._polyfillVersion = 'v57';

                console.log('[Polyfill v57] JavaScript 兼容性脚本注入完成');

            })();
        """.trimIndent()
    }

    /**
     * 获取课表数据就绪检测脚本
     * @return JavaScript 代码，返回 "true" 或 "false"
     */
    fun getCourseDataCheckScript(): String {
        return """
            (function() {
                if (typeof window.checkCourseDataReady === 'function') {
                    return window.checkCourseDataReady() ? 'true' : 'false';
                }
                return 'false';
            })();
        """.trimIndent()
    }

    /**
     * 获取页面类型检测脚本
     * @return JavaScript 代码，返回 JSON 字符串
     */
    fun getPageTypeDetectScript(): String {
        return """
            (function() {
                if (typeof window.detectPageType === 'function') {
                    return JSON.stringify(window.detectPageType());
                }
                return JSON.stringify({type: 'unknown', url: window.location.href});
            })();
        """.trimIndent()
    }

    /**
     * 获取 JavaScript 错误收集脚本
     * @return JavaScript 代码，返回 JSON 字符串
     */
    fun getErrorsScript(): String {
        return """
            (function() {
                return JSON.stringify(window.jsErrors || []);
            })();
        """.trimIndent()
    }

    /**
     * 获取页面诊断脚本 (v51)
     * 用于调试，收集页面状态信息
     * @return JavaScript 代码，返回 JSON 字符串
     */
    fun getDiagnosticScript(): String {
        return """
            (function() {
                var result = {
                    url: window.location.href,
                    polyfillInjected: window._polyfillInjected || false,
                    polyfillVersion: window._polyfillVersion || 'none',
                    unitCount: typeof window.unitCount !== 'undefined' ? window.unitCount : 'undefined',
                    table0Exists: typeof window.table0 !== 'undefined',
                    activitiesCount: 0,
                    actualDataCount: 0,
                    nonEmptySlots: 0,
                    courseName: typeof window.courseName !== 'undefined' ? window.courseName : 'undefined',
                    infoTitleCount: document.querySelectorAll('td.infoTitle').length,
                    jsErrors: window.jsErrors || [],
                    documentReady: document.readyState,
                    scriptsCount: document.querySelectorAll('script').length,
                    timestamp: new Date().toISOString()
                };

                // [v51] 详细统计课程数据
                if (window.table0 && window.table0.activities && Array.isArray(window.table0.activities)) {
                    result.activitiesCount = window.table0.activities.length;
                    for (var i = 0; i < window.table0.activities.length; i++) {
                        if (window.table0.activities[i] && window.table0.activities[i].length > 0) {
                            result.actualDataCount += window.table0.activities[i].length;
                            result.nonEmptySlots++;
                        }
                    }
                }

                return JSON.stringify(result);
            })();
        """.trimIndent()
    }

    /**
     * [v54] 获取登录页面检测脚本
     * 基于页面内容而非仅 URL 判断是否是登录页面
     * @return JavaScript 代码，返回 JSON 字符串
     */
    fun getLoginPageDetectionScript(): String {
        return """
            (function() {
                try {
                    // 登录页面特征检测
                    var loginIndicators = [
                        // 统一认证页面容器
                        document.querySelector('.auth_login_content') !== null,
                        document.querySelector('.auth_page_body') !== null,
                        document.querySelector('.auth_page_wrappers') !== null,
                        // 用户名/密码输入框
                        document.getElementById('username') !== null,
                        document.getElementById('password') !== null,
                        document.querySelector('input[type="password"]') !== null,
                        document.querySelector('input[name="username"]') !== null,
                        // 登录按钮
                        document.getElementById('login_submit') !== null,
                        document.querySelector('button[type="submit"]') !== null,
                        // URL 包含 ids.chd.edu.cn
                        window.location.href.indexOf('ids.chd.edu.cn') > -1,
                        // 标题包含"统一身份认证"
                        document.title.indexOf('统一身份认证') > -1 || document.title.indexOf('Login') > -1,
                        // 登录表单特征
                        document.querySelector('form[action*="login"]') !== null,
                        // 登录页面特有的 CSS
                        document.querySelector('link[href*="login.css"]') !== null
                    ];

                    var isLoginPage = loginIndicators.some(function(v) { return v; });

                    // 已登录特征检测
                    var loggedInIndicators = [
                        // 用户名链接（包含学号）
                        document.querySelector('a[href*="security/my.action"]') !== null,
                        // 退出按钮
                        document.querySelector('a[href*="logout.action"]') !== null,
                        // beangle 框架特征（banner 区域）
                        document.querySelector('.banner') !== null && document.querySelector('.banner_area') !== null,
                        // jQuery struts2 特征
                        typeof window.jQuery !== 'undefined' &&
                            typeof window.jQuery.struts2_jquery !== 'undefined',
                        // 教务系统特有的菜单
                        document.querySelector('ul.menu') !== null || document.querySelector('#menu_panel') !== null
                    ];

                    var isLoggedIn = loggedInIndicators.some(function(v) { return v; });

                    // 检测用户名显示
                    var usernameDisplay = '';
                    var userLink = document.querySelector('a[href*="security/my.action"]');
                    if (userLink && userLink.textContent) {
                        usernameDisplay = userLink.textContent.trim();
                        // 如果用户名包含学号格式，说明已登录
                        if (usernameDisplay.indexOf('(') > -1) {
                            isLoggedIn = true;
                        }
                    }

                    return JSON.stringify({
                        isLoginPage: isLoginPage,
                        isLoggedIn: isLoggedIn && !isLoginPage,
                        usernameDisplay: usernameDisplay,
                        title: document.title,
                        url: window.location.href,
                        loginIndicatorCount: loginIndicators.filter(function(v) { return v; }).length,
                        loggedInIndicatorCount: loggedInIndicators.filter(function(v) { return v; }).length
                    });
                } catch (e) {
                    return JSON.stringify({
                        isLoginPage: false,
                        isLoggedIn: false,
                        error: e.message
                    });
                }
            })();
        """.trimIndent()
    }
}
