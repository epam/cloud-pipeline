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
    ;

Arithmetic
    :                                                 -> new yy.IncompleteLogicalExpression(undefined, undefined, undefined, {total: @0})
    | PROPERTY OPERAND Value                          -> new yy.LogicalExpression($1, $2, $3, {property: @1, operand: @2, value: @3, total: @0})
    | PROPERTY OPERAND Value BracketList              -> new yy.LogicalExpression($1, $2, $3, {property: @1, operand: @2, value: @3, total: @0})
    | PROPERTY                                        -> new yy.IncompleteLogicalExpression($1, undefined, undefined, {property: @1, total: @0})
    | BracketList                                     -> new yy.IncompleteLogicalExpression(undefined, undefined, undefined, {total: @0})
    | BracketList PROPERTY OPERAND Value              -> new yy.LogicalExpression($2, $3, $4, {property: @2, operand: @3, value: @3, total: @0})
    | BracketList PROPERTY                            -> new yy.IncompleteLogicalExpression($2, undefined, undefined, {property: @2, total: @0})
    | BracketList PROPERTY OPERAND Value BracketList  -> new yy.LogicalExpression($2, $3, $4, {property: @2, operand: @3, value: @3, total: @0})
    ;

BracketList
    : '('
    | BracketList '('
    | ')'
    | BracketList ')'
    ;

Value
    :
    | PROPERTY
    | STRING
    ;

//////////////////////////////////////////////////////////////////////////////
//  User code
//////////////////////////////////////////////////////////////////////////////

%%
