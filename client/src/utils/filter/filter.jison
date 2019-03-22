//////////////////////////////////////////////////////////////////////////////
//  User code
//////////////////////////////////////////////////////////////////////////////

%{
%}

//////////////////////////////////////////////////////////////////////////////
//  Lexical grammar
//////////////////////////////////////////////////////////////////////////////

%lex
%options case-insensitive

PROPERTY                [a-zA-Z0-9\._\-@*%]+
ARITHMETIC_OPERAND      ("<="|"<"|"="|"!="|">="|">")
STRING                  (?:\"([^"]*)\"|\'([^']*)\'|([^\s'"]+))

%%

\s+                     /* skip whitespace */
{ARITHMETIC_OPERAND}    return 'OPERAND';

OR                      return 'OR';
AND                     return 'AND';
NOT                     return 'NOT';

{PROPERTY}              return 'PROPERTY';

"("                     return '(';
")"                     return ')';
{STRING}                return 'STRING';
<<EOF>>                 return 'EOF';
.                       return 'INVALID';

/lex

//////////////////////////////////////////////////////////////////////////////
//  Operator associations and precedence
//////////////////////////////////////////////////////////////////////////////

%left 'OR'
%left 'AND'
%left 'NOT'

%start Program

//////////////////////////////////////////////////////////////////////////////
//  Language grammar
//////////////////////////////////////////////////////////////////////////////

%%

Program
    : Expression EOF                   { return $1; }
    ;

Expression
    : Arithmetic
    | Expression OR Expression      -> new yy.OrExpression($1, $3)
    | Expression AND Expression     -> new yy.AndExpression($1, $3)
    | '(' Expression ')'            -> $2
    ;

Arithmetic
    : PROPERTY OPERAND Value           -> new yy.LogicalExpression($1, $2, $3)
    ;

Value
    : PROPERTY
    | STRING
    ;

//////////////////////////////////////////////////////////////////////////////
//  User code
//////////////////////////////////////////////////////////////////////////////

%%
