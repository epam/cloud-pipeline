/**
 * Parses a logical expression string and converts it into a structured Criteria object.
 *
 * @param {string} criteria - The logical expression string to parse.
 * @returns {Object} A structured Criteria object representing the parsed expression.
 *
 * The Criteria object can be of two types:
 *
 * 1. LogicalCriteria: {"operator": "NOT" | "OR" | "AND", "operands": Criteria[], "type": "logical"}
 * 2. Expression: {"type": "expression", "parameter": <parameter>, "value": <optional value>}
 *
 * The function supports:
 * - Simple expressions like:
 *   "PARAM1 = VALUE"
 *   "PARAM2"
 *
 * - Logical expressions with AND, OR, and NOT operators:
 *   "PARAM1 = 'Some value' AND PARAM2"
 *   "NOT (PARAM3 OR PARAM4 = 100)"
 *   "PARAM5 = 'Test' OR PARAM6 = 42"
 *
 * - Parentheses to enforce precedence:
 *   "(PARAM1 = 'Value' AND PARAM2) OR PARAM3"
 *
 * Example usage:
 *
 * @example
 * parseCriteria("not (PARAM1 = 'Param1 value' and PARAM2) or PARAM3 = 333");
 *
 * // This will output a structured object representation of the logical expression.
 */
export function parseCriteria (criteria = '') {
  criteria = criteria.trim();

  function tokenize (input) {
    const tokens = [];
    const regex = /\s*(\(|\)|\bNOT\b|\bAND\b|\bOR\b|==|!=|=|!|"[^"]*"|'[^']*'|[^\s=!()]+)\s*/gi;
    let match;
    while ((match = regex.exec(input)) !== null) {
      tokens.push(match[1]);
    }
    return tokens;
  }

  function parseExpression (tokens) {
    let token = tokens.shift();
    if (!token) return null;

    if (token === '(') {
      let expr = parseLogical(tokens);
      tokens.shift(); // Remove closing ')'
      return expr;
    }

    if (token.toUpperCase() === 'NOT' || token === '!') {
      return {type: 'logical', operator: 'NOT', operands: [parseExpression(tokens)]};
    }

    let param = token;
    if (tokens[0] === '=' || tokens[0] === '==') {
      tokens.shift();
      let value = tokens.shift();
      if (value.startsWith("'") || value.startsWith('"')) {
        value = value.slice(1, -1);
      }
      return {type: 'expression', parameter: param, value: value};
    }
    if (tokens[0] === '!=') {
      tokens.shift();
      let value = tokens.shift();
      if (value.startsWith("'") || value.startsWith('"')) {
        value = value.slice(1, -1);
      }
      return {
        type: 'logical',
        operands: [{type: 'expression', parameter: param, value: value}],
        operator: 'NOT'
      };
    }
    return {type: 'expression', parameter: param, value: true};
  }

  function parseLogical (tokens) {
    let left = parseExpression(tokens);

    while (tokens.length > 0) {
      let op = tokens[0].toUpperCase();
      if (op !== 'AND' && op !== 'OR') break;
      tokens.shift();
      let right = parseExpression(tokens);
      left = {type: 'logical', operator: op, operands: [left, right]};
    }
    return left;
  }

  return parseLogical(tokenize(criteria));
}

function getRunParameterValue (run, parameter) {
  const {
    pipelineRunParameters = []
  } = run || {};
  const p = pipelineRunParameters
    .find((pp) => pp.name && pp.name.toLowerCase() === parameter.toLowerCase());
  return p ? p.value : undefined;
}

function criteriaMatches (run, criteria, verbose = false) {
  if (!criteria) {
    return false;
  }
  if (typeof criteria === 'boolean') {
    return criteria;
  }
  if (typeof criteria !== 'object') {
    return false;
  }
  const {
    type
  } = criteria;
  if (type && typeof type === 'string') {
    switch (type.toLowerCase()) {
      case 'logical':
        return logicalCriteriaMatches(run, criteria, verbose);
      case 'expression':
        return expressionMatches(run, criteria, verbose);
      default:
        return false;
    }
  }
  return false;
}

function logicalCriteriaMatches (run, logical, verbose = false) {
  const {
    operator,
    operands = []
  } = logical || {};
  if (!operator) {
    return false;
  }
  if (verbose) {
    console.groupCollapsed(`logical ${operator}`);
  }
  try {
    const logicalResult = (() => {
      if (/^NOT$/i.test(operator)) {
        if (operands.length !== 1) {
          if (verbose) {
            console.log(`unexpected number of operands: ${operands.length}`);
          }
          return false;
        }
        return !criteriaMatches(run, operands[0], verbose);
      }
      let result = operator.toUpperCase() === 'AND';
      for (const operand of operands) {
        const operandResult = criteriaMatches(run, operand, verbose);
        switch (operator.toUpperCase()) {
          case 'AND':
            result = result && operandResult;
            break;
          case 'OR':
            result = result || operandResult;
            break;
          default:
            break;
        }
        if (!result && operator.toUpperCase() === 'AND') {
          break;
        }
      }
      return result;
    })();
    if (verbose) {
      console.log('check result:', logicalResult);
    }
    return logicalResult;
  } finally {
    if (verbose) {
      console.groupEnd();
    }
  }
}

function expressionMatches (run, expression, verbose = false) {
  const {
    parameter,
    value = true
  } = expression || {};
  if (!parameter) {
    if (verbose) {
      console.log('parameter not specified');
    }
    return false;
  }
  if (verbose) {
    console.groupCollapsed(parameter);
  }
  try {
    const val = getRunParameterValue(run, parameter);
    if (verbose) {
      console.log('parameter:', parameter);
      console.log('value to check:', value);
      console.log('parameter value:', val);
    }
    const result = (() => {
      if (typeof value === 'boolean') {
        return Boolean(val) === value;
      }
      return val === undefined ? false : `${val}` === `${value}`;
    })();
    if (verbose) {
      console.log('check result:', result);
    }
    return result;
  } finally {
    if (verbose) {
      console.groupEnd();
    }
  }
}

export function checkCriteria (run, parsedCriteria, options = {}) {
  if (!run || !parsedCriteria) {
    return false;
  }
  const {
    verbose = false,
    action: name
  } = options || {};
  try {
    if (verbose) {
      console.groupCollapsed(
        name
          ? `run #${run.id} action "${name}" availability check`
          : `run #${run.id} action availability check`
      );
    }
    const result = criteriaMatches(run, parsedCriteria, verbose);
    if (verbose) {
      console.log('check result:', result);
    }
    return result;
  } catch (e) {
    if (verbose) {
      console.warn(e);
    }
  } finally {
    if (verbose) {
      console.groupEnd();
    }
  }
}

export function parseRunActionCriteria (action, criteria) {
  if (criteria === undefined || criteria === null) {
    return () => true;
  }
  if (typeof criteria === 'boolean') {
    return () => criteria;
  }
  if (typeof criteria === 'string') {
    try {
      const parsed = parseCriteria(criteria);
      const verbosity = new Set();
      return (run) => {
        const verbose = run ? !verbosity.has(run.id) : false;
        if (run) {
          verbosity.add(run.id);
        }
        return checkCriteria(run, parsed, {verbose, action});
      };
    } catch (error) {
      console.log('error parsing criteria:');
      console.log(criteria);
      console.error(error);
      return () => false;
    }
  }
  console.log(`unsupported criteria format, expected boolean or string, got ${typeof criteria}`);
  return () => false;
}
