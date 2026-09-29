/*
 * Derived from babel-plugin-track-usage, Copyright Quinscape GmbH, used under the
 * Apache License, Version 2.0. Vendored from commit
 * 1aa8c7c451a3683390ba991e6a19e89dc1c5e5e2 and converted to ES modules. See the
 * NOTICE file at the root of this repository for the full statement of changes.
 *
 * Left as JavaScript rather than ported: 700 lines of babel AST walking with nothing
 * to carry the types of, where a port would be a rewrite. What it offers the rest of
 * the package is declared in trackUsagePlugin.d.ts.
 */
import nodeJsPath from "node:path";
import deepEqual from "deep-equal";

import Data from "./trackUsageData.js";

const SLASH_RE = new RegExp("\\" + nodeJsPath.sep, "g")

const FAILED = "__FAILED__";

function strip(path, sourceRoot)
{
    if (nodeJsPath.sep !== "/")
    {
        path = path.replace( SLASH_RE, "/")
    }

    //console.log("STRIP", path, sourceRoot);

    if (sourceRoot)
    {
        if (path.indexOf(sourceRoot) !== 0)
        {
            return null;
        }

        return path.substring(sourceRoot.length);
    }
    return path;
}


function evalRule(path, parts)
{
    let current = path;
    let isPath = true;
    let last = parts.length - 1;
    for (let i = 0; i <= last; i++)
    {
        const part = parts[i]
        if (isPath && part === "parent")
        {
            current = current.parentPath
        }
        else
        {
            if (isPath)
            {
                current = current.node[part];
            }
            else
            {
                current = current[part];
            }

            if ( i < last && (!current || typeof current !== "object") )
            {
                return FAILED;
            }
            isPath = false;
        }
    }

    return isPath ? current.node : current
}

const preparedContextSym = Symbol("PreparedContext");

function prepareContext(tf, rules)
{

    const existing = tf[preparedContextSym];
    if (existing)
    {
        //console.log("REUSE", rules)
        return existing
    }
    //console.log("PREPARE", rules)

    let preparedRules, isArray = false;
    if (typeof rules === "string")
    {
        preparedRules = rules.split(".")
    }
    else if (Array.isArray(rules))
    {
        isArray = true;
        let arrayRules = []
        for (let i = 0; i < rules.length; i++)
        {
            const rule = rules[i]

            if (typeof rule !== "string")
            {
                throw new Error("Invalid context rule: " + rule)
            }

            arrayRules.push(rule.split("."))
        }

        preparedRules = arrayRules
    }
    else if (rules && typeof rules === "object")
    {
        let objectRules = {}
        for (let key in rules)
        {
            if (rules.hasOwnProperty(key))
            {
                let rule = rules[key]

                if (typeof rule !== "string")
                {
                    throw new Error("Invalid context rule: " + rule)
                }

                objectRules[key] = rule.split(".")
            }
        }
        
        preparedRules = objectRules
    }
    else
    {
        throw new Error("Invalid context rule: " + rules)
    }

    const result = [preparedRules, isArray]
    tf[preparedContextSym] =  result;
    return result;
}


function captureContext(path, tf)
{
    const [rules, wasArray] = prepareContext(tf, tf.captureContext)

    if (Array.isArray(rules))
    {
        if (!wasArray)
        {
            return evalRule(path, rules)
        }
        else
        {
            let context = []
            for (let i = 0; i < rules.length; i++)
            {
                context.push(evalRule(path,rules[i]))
            }
            return context
        }
    }
    else if (rules && typeof rules === "object")
    {
        let context = {}
        for (let key in rules)
        {
            if (rules.hasOwnProperty(key))
            {
                context[key] = evalRule(path, rules[key])
            }
        }
        return context
    }
}


export default function trackUsage(t) {

    t = t.types;

    function insert(set, indexes, value, loc, contexts, ctx)
    {
        for (let i = 0; i < set.length; i++)
        {
            const v = set[i]
            if (deepEqual(v, value))
            {
                return;
            }
        }

        set.push(value);
        if (indexes)
        {
            indexes.push(loc)
        }
        if (contexts)
        {
            contexts.push(ctx)
        }
    }

    function recordCall(data, path, pluginOpts, memberCall, importedFnCall)
    {
        const node = path.node
        let record, indexRecord, contextRecord
        const callee = node.callee

        const array = memberCall ?
            data._config.trackedByVar[callee.object.name] :
            data._config.trackedByVar[callee.name]
        for (let i = 0; i < array.length; i++)
        {
            const e = array[i]
            const tf = data._config.trackedFunctions[e.name]

            if ((memberCall && tf.fn) || (!memberCall && !tf.fn) || (!memberCall && importedFnCall && tf.fn))
            {
                if (data._config.debug)
                {
                    console.log("Tracking ", node)
                }

                if (!tf.fn || importedFnCall || e.expr(callee))
                {
                    const args = node.arguments
                    const varArgs = tf.varArgs

                    const argsEnd = Math.min(
                        args.length,
                        varArgs ? (
                            typeof varArgs === "number" && varArgs > 0 ? varArgs : 1
                        ) : Infinity
                    )

                    // if (node.arguments.length === 1 || (varArgs && node.arguments.length >= 1))
                    // {
                    record = data.calls[e.name];
                    indexRecord = pluginOpts.indexes && data.indexes[e.name];
                    contextRecord = tf.captureContext && data.contexts[e.name];
                    if (!record)
                    {
                        record = [];
                        data.calls[e.name] = record;

                        if (pluginOpts.indexes)
                        {
                            indexRecord = []
                            data.indexes[e.name] = indexRecord;
                        }
                        if (tf.captureContext)
                        {
                            contextRecord = []
                            data.contexts[e.name] = contextRecord;
                        }
                    }

                    let values = []

                    const staticCalls = {scope: path.scope, modules: pluginOpts.staticModules || {}, produced: new WeakSet()}

                    let allEvaluated = true
                    for (let j = 0; j < argsEnd; j++)
                    {
                        const failure = {node: null, path: []}
                        const evaluated = staticEval(
                            args[j],
                            tf.allowIdentifier,
                            failure,
                            staticCalls
                        )
                        if (evaluated === undefined)
                        {
                            allEvaluated = false;
                            if (data._config.debug)
                            {
                                console.log("Value evaluated to undefined");
                            }
                            if (pluginOpts.onSkippedCall)
                            {
                                reportSkippedCall(pluginOpts.onSkippedCall, path, e.name, j, failure)
                            }
                            break;
                        }

                        values.push(
                            evaluated
                        );
                    }

                    //console.log("EVAL", node.arguments , " => ", values);

                    if (allEvaluated)
                    {
                        // what a static call produced is an object of the module it came from, recorded as the
                        // plain data it serializes to
                        values = JSON.parse(JSON.stringify(values))

                        if (data._config.debug)
                        {
                            console.log("Record '" + values + "' for " + e.name);
                        }
                        insert(
                            record,
                            indexRecord,
                            values,
                            [node.start, node.end],
                            contextRecord,
                            contextRecord && captureContext(path, tf)
                        );
                    }
                    // }
                    // else if (data._config.debug)
                    // {
                    //     console.log("Number of arguments doesn't match");
                    // }
                }
            }
        }
    }

    function ownValue(object, key)
    {
        return Object.prototype.hasOwnProperty.call(object, key) ? object[key] : undefined
    }

    /**
     * What an identifier is imported as, if it is imported from one of the static modules: the export
     * itself for a named import, all of them for a namespace import. Resolved through the scope, so a local
     * variable of the same name is not mistaken for it.
     */
    function staticImport(name, staticCalls)
    {
        const binding = staticCalls.scope.getBinding(name)
        if (!binding || binding.kind !== "module")
        {
            return undefined
        }

        const specifier = binding.path.node
        const exports = ownValue(staticCalls.modules, binding.path.parent.source.value)
        if (!exports)
        {
            return undefined
        }

        if (t.isImportSpecifier(specifier))
        {
            return ownValue(exports, t.isIdentifier(specifier.imported) ? specifier.imported.name : specifier.imported.value)
        }
        if (t.isImportNamespaceSpecifier(specifier))
        {
            return exports
        }
        return undefined
    }

    /**
     * The function a static call calls, and the receiver to call it on: an imported export, `field(...)`,
     * a member of an imported object, `FilterDSL.field(...)`, or a method of a value a static call produced,
     * `field(...).eq(...)`. Only a method of that value's own prototype chain counts, not one every object
     * has.
     */
    function staticCallee(callee, failure, staticCalls)
    {
        if (t.isIdentifier(callee))
        {
            const fn = staticImport(callee.name, staticCalls)
            return typeof fn === "function" ? {fn: fn, receiver: undefined} : undefined
        }

        if (!t.isMemberExpression(callee) || callee.computed || !t.isIdentifier(callee.property))
        {
            return undefined
        }

        const name = callee.property.name
        if (t.isIdentifier(callee.object))
        {
            const imported = staticImport(callee.object.name, staticCalls)
            if (imported && typeof imported === "object")
            {
                const fn = ownValue(imported, name)
                return typeof fn === "function" ? {fn: fn, receiver: undefined} : undefined
            }
        }

        if (!t.isCallExpression(callee.object))
        {
            // a variable, say: nothing here knows what it holds
            failure.node = callee.object
            return undefined
        }

        const receiver = staticCall(callee.object, failure, staticCalls)
        if (!receiver || typeof receiver !== "object" || !staticCalls.produced.has(receiver))
        {
            return undefined
        }

        for (let proto = Object.getPrototypeOf(receiver); proto && proto !== Object.prototype; proto = Object.getPrototypeOf(proto))
        {
            const fn = ownValue(proto, name)
            if (typeof fn === "function" && name !== "constructor")
            {
                return {fn: fn, receiver: receiver}
            }
        }
        return undefined
    }

    /**
     * Makes a static call and returns its result, undefined if the call is not one: the callee is not one
     * of the static modules' exports, an argument is not static, or the call throws. The failure record
     * receives the innermost part that is not static.
     */
    function staticCall(node, failure, staticCalls)
    {
        const callee = staticCallee(node.callee, failure, staticCalls)
        if (!callee)
        {
            failure.node = failure.node || node
            return undefined
        }

        const args = []
        for (let i = 0; i < node.arguments.length; i++)
        {
            const arg = node.arguments[i]
            const evaluated = t.isSpreadElement(arg) ? undefined : staticEval(arg, false, failure, staticCalls)
            if (evaluated === undefined)
            {
                failure.node = failure.node || arg
                return undefined
            }
            args.push(evaluated)
        }

        let result
        try
        {
            result = callee.fn.apply(callee.receiver, args)
        }
        catch (e)
        {
            failure.node = node
            return undefined
        }

        if (result && typeof result === "object")
        {
            staticCalls.produced.add(result)
        }
        return result
    }

    /**
     * Reports a tracked call left out of the data because an argument did not evaluate, with the
     * innermost part of it that did not.
     */
    function reportSkippedCall(onSkippedCall, path, name, argument, failure)
    {
        const file = path.hub.file
        const node = failure.node || path.node.arguments[argument]
        const loc = node.loc || path.node.loc

        onSkippedCall({
            name: name,
            filename: file.opts.filename,
            line: loc ? loc.start.line : null,
            column: loc ? loc.start.column + 1 : null,
            argument: argument,
            path: failure.path.join(""),
            code: file.code.slice(node.start, node.end)
        })
    }

    /**
     * Evaluates a literal expression to its value, undefined if it is not one. Where it is not, the
     * failure record, if given, receives the node that stopped the evaluation and the property path
     * leading to it (".config", ".sortFields", "[0]").
     *
     * With staticCalls given, a call of one of its modules' exports counts as static too, and so does a
     * method call on what such a call produced: the call is made, here and now, and its result is the value.
     */
    function staticEval(node, allowIdentifier, failure, staticCalls)
    {
        let i, out, evaluatedValue

        if (t.isTemplateLiteral(node))
        {
            if (node.expressions.length > 0)
            {
                throw new Error("Extracted template literals can't contain expressions");
            }

            return node.quasis[0].value.raw;
        }
        else if (t.isNullLiteral(node))
        {
            return null;
        }
        else if (t.isLiteral(node))
        {
            return node.value;
        }
        else if (t.isArrayExpression(node))
        {
            const elements = node.elements
            out = new Array(elements.length);
            for (i = 0; i < elements.length; i++)
            {
                evaluatedValue = staticEval(elements[i], allowIdentifier, failure, staticCalls);

                if (evaluatedValue !== undefined)
                {
                    out[i] = evaluatedValue;
                }
                else
                {
                    // non-literal array element -> bail
                    if (failure)
                    {
                        failure.node = failure.node || elements[i] || node
                        failure.path.unshift("[" + i + "]")
                    }
                    return undefined;
                }
            }
            return out;
        }
        else if (t.isObjectExpression(node))
        {
            const properties = node.properties
            out = {};
            for (i = 0; i < properties.length; i++)
            {
                let key
                const property = properties[i]
                if (t.isLiteral(property.key))
                {
                    key = property.key.value;
                }
                else if (t.isIdentifier(property.key))
                {
                    key = property.key.name;
                }
                else
                {
                    // computed property -> bail
                    if (failure)
                    {
                        failure.node = property
                    }
                    return undefined;
                }

                evaluatedValue = staticEval(property.value, allowIdentifier, failure, staticCalls);

                if (evaluatedValue !== undefined)
                {
                    out[key] = evaluatedValue;
                }
                else
                {
                    // non-literal value -> bail
                    if (failure)
                    {
                        failure.path.unshift("." + key)
                    }
                    return undefined;
                }
            }
            return out;
        }
        else if (allowIdentifier && t.isIdentifier(node))
        {
            return { __identifier: node.name };
        }
        else if (staticCalls && t.isCallExpression(node))
        {
            return staticCall(node, failure, staticCalls);
        }

        if (failure)
        {
            failure.node = node
        }
        return undefined;
    }

    /**
     * The normal filenameRelative seems not to be updated with our setup, so we find the relative
     * path ourselves with the sourceRoot option being set.
     * @param opts
     */
    function getRelativeModuleName(opts)
    {
        //console.log("getRelativeModuleName", opts.sourceRoot);

        const root = opts.root || opts.sourceRoot
        if (!root)
        {
            return null;
        }
        const len = root.length
        const fullWithExtension = opts.filename.substring(root[len - 1] === nodeJsPath.sep ? len : len + 1)

        return fullWithExtension.substring(0, fullWithExtension.lastIndexOf("."));
    }

    /**
     * Returns true if the given node is a call expression for require
     *
     * @param node
     * @returns {boolean}
     */
    function isRequire(node)
    {
        if (!node || !t.isCallExpression(node))
        {
            return false;
        }

        if (node.callee.type !== "Identifier" || node.callee.name !== "require")
        {
            return false;
        }

        // no call arguments
        const args = node.arguments
        if (args.length !== 1)
        {
            return false;
        }

        // first node arg is not an object
        const first = args[0]
        return t.isLiteral(first);
    }

    function trackVar(data, state, varName, modulePath, importedName, module)
    {
        const pluginOpts = state.opts

        if (modulePath[0] === ".")
        {
            // resolve relative module ("/../" to go back from the view to its directory)

            const relRequired = strip(nodeJsPath.normalize(module + "/../" + modulePath), pluginOpts.sourceRoot)

            if (!relRequired)
            {
                return;
            }

            modulePath = "./" + relRequired;
        }
        data.requires[varName] = modulePath;

        const array = data._config && data._config.moduleLookup[modulePath]
        if (array)
        {
            // copy tracked function association to local variable

            const out = []

            for (let i = 0; i < array.length; i++)
            {
                const name = array[i]
                const tf = data._config.trackedFunctions[name]
                const fn = tf.fn
                if (fn)
                {
                    if (importedName)
                    {
                        if (importedName === fn)
                        {
                            data._config.isMemberCall[varName] = true;
                        } else
                        {
                            continue;
                        }
                    } else
                    {
                        data._config.hasMemberCall[varName] = true;
                    }

                } else
                {
                    if (!importedName)
                    {
                        data._config.hasModuleCall[varName] = true;
                    } else
                    {
                        continue;
                    }
                }

                out.push({
                    name: name,
                    fn: importedName,
                    expr: !!fn && !importedName && t.buildMatchMemberExpression(varName + "." + fn)
                });
            }

            data._config.trackedByVar[varName] = out;

            if (state.opts.debug)
            {
                console.log("Tracked module '" + modulePath + "' assigned to variable " + varName)
            }
        }
    };
    return {
        visitor: {
            "Program": function (path, state) {
                //dump(path.node);

                const pluginOpts = state.opts
                const module = getRelativeModuleName(path.hub.file.opts)
                if (!module)
                {
                    if (state.opts.debug)
                    {
                        console.log("No source root, ignoring everything");
                    }
                    return;
                }

                const relative = strip(module, pluginOpts.sourceRoot)

                if (!relative)
                {
                    return;
                }

                const data = Data._internal()["./" + relative] = {
                    //module: module,
                    requires: {},
                    calls: {}
                }

                if (pluginOpts.indexes)
                {
                    data.indexes = {};
                }

                const moduleLookup = {}

                let contextUsed = false;
                const trackedFunctions = pluginOpts.trackedFunctions
                for (let name in trackedFunctions)
                {
                    if (trackedFunctions.hasOwnProperty(name))
                    {
                        const tf = trackedFunctions[name]
                        let array = moduleLookup[tf.module]
                        if (!array)
                        {
                            array = [name];
                            moduleLookup[tf.module] = array;
                        } else
                        {
                            array.push(name);
                        }

                        if (tf.captureContext && !data.contexts)
                        {
                            data.contexts = {};
                        }
                    }
                }



                data._config = {
                    trackedFunctions: trackedFunctions,
                    debug: !!pluginOpts.debug,
                    moduleLookup: moduleLookup,
                    trackedByVar: {},
                    hasMemberCall: {},
                    isMemberCall: {},
                    hasModuleCall: {}
                };

                if (state.opts.debug)
                {
                    console.log("Analysing './" + module + "'");
                }
            },
            "AssignmentExpression|VariableDeclarator": function (path, state) {
                //dir("state", state);
                const pluginOpts = state.opts
                const node = path.node
                const scope = path.scope
                if (scope.parent)
                {
                    // we only want to examine the root scope
                    return;
                }

                let left, right

                const module = getRelativeModuleName(path.hub.file.opts)
                if (!module)
                {
                    return;
                }

                const relative = strip(module, pluginOpts.sourceRoot)

                if (!relative)
                {
                    return;
                }
                const data = Data._internal()["./" + relative]

                const nodeIsAssignment = t.isAssignmentExpression(node)
                const nodeIsVariableDeclarator = t.isVariableDeclarator(node)
                if (nodeIsAssignment)
                {
                    left = node.left;
                    right = node.right;
                } else if (nodeIsVariableDeclarator)
                {
                    left = node.id;
                    right = node.init;
                }

                if (t.isIdentifier(left) && isRequire(right))
                {
                    trackVar(data, state, left.name, right.arguments[0].value, null, module);
                }
            },
            "CallExpression|NewExpression": function (path, state) {
                const pluginOpts = state.opts
                const node = path.node

                if (!path.hub)
                {
                    return;
                }

                const module = getRelativeModuleName(path.hub.file.opts)
                if (!module)
                {
                    return;
                }
                const relative = strip(module, pluginOpts.sourceRoot)

                if (!relative)
                {
                    return;
                }
                const data = Data._internal()["./" + relative]

                const callee = node.callee

                if (!data._config)
                {
                    return;
                }

                if (t.isIdentifier(callee) && data._config.hasModuleCall[callee.name])
                {
                    if (state.opts.debug)
                    {
                        console.log("Module call for variable " + callee.name + "");
                    }
                    recordCall(data, path, pluginOpts, false, false);
                } else if (t.isIdentifier(callee) && data._config.isMemberCall[callee.name])
                {
                    if (state.opts.debug)
                    {
                        console.log("Imported member call for variable " + callee.name + "");
                    }
                    recordCall(data, path, pluginOpts, false, true);
                } else if (t.isMemberExpression(callee) && t.isIdentifier(
                    callee.object) && data._config.hasMemberCall[callee.object.name])
                {
                    if (state.opts.debug)
                    {
                        console.log("Member call for variable " + callee.object.name + "");
                    }
                    recordCall(data, path, pluginOpts, true, false);
                }
            },
            "ImportDeclaration": function (path, state) {
                let i
                const node = path.node
                let specifier
                const pluginOpts = state.opts

                const module = getRelativeModuleName(path.hub.file.opts)
                if (!module)
                {
                    return;
                }

                const relative = strip(module, pluginOpts.sourceRoot)

                if (!relative)
                {
                    return;
                }
                const data = Data._internal()["./" + relative]

                for (i = 0; i < node.specifiers.length; i++)
                {
                    specifier = node.specifiers[i];

                    if (t.isImportDefaultSpecifier(specifier))
                    {
                        trackVar(data, state, specifier.local.name, node.source.value, null, module);
                    } else if (t.isImportSpecifier(specifier))
                    {
                        trackVar(data, state, specifier.local.name, node.source.value, specifier.imported.name, module);

                        //console.log(specifier.imported, specifier.local);
                    }
                }
            }
        }
    };
}
