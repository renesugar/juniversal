/*
 * Copyright (c) 2012-2015, Microsoft Mobile
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.juniversal.translator.csharp;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.juniversal.translator.core.JUniversalException;

import java.util.HashMap;

import static org.juniversal.translator.core.ASTUtil.forEach;


public class InfixExpressionWriter extends CSharpASTNodeWriter<InfixExpression> {
    private HashMap<InfixExpression.Operator, String> equivalentOperators;  // Operators that have the same token in both Java & C#

    public InfixExpressionWriter(CSharpSourceFileWriter cSharpASTWriters) {
        super(cSharpASTWriters);

        /*
        Java binary operator precedence, from: http://docs.oracle.com/javase/tutorial/java/nutsandbolts/operators.html
        postfix	expr++ expr--
        unary	++expr --expr +expr -expr ~ !
        multiplicative	* / %
        additive	+ -
        shift	<< >> >>>
        relational	< > <= >= instanceof
        equality	== !=
        bitwise AND	&
        bitwise exclusive OR	^
        bitwise inclusive OR	|
        logical AND	&&
        logical OR	||
        ternary	? :
        assignment	= += -= *= /= %= &= ^= |= <<= >>= >>>=


        C# operator precendence, from C# 5.0 language spec
        Primary	x.y  f(x)  a[x]  x++  x--  new
        typeof  default  checked  unchecked  delegate
        7.7
        Unary	+  -  !  ~  ++x  --x  (T)x
        7.8
        Multiplicative	*  /  %
        7.8
        Additive	+  -
        7.9
        Shift	<<  >>
        7.10
        Relational and type testing	<  >  <=  >=  is  as
        7.10
        Equality	==  !=
        7.11
        Logical AND	&
        7.11
        Logical XOR	^
        7.11
        Logical OR	|
        7.12
        Conditional AND	&&
        7.12
        Conditional OR	||
        7.13
        Null coalescing	??
        7.14
        Conditional	?:
        7.17, 7.15
        Assignment and lambda expression	=  *=  /=  %=  +=  -=  <<=  >>=  &=  ^=  |=
        =>
        */

        equivalentOperators = new HashMap<>();
        equivalentOperators.put(InfixExpression.Operator.TIMES, "*");
        equivalentOperators.put(InfixExpression.Operator.DIVIDE, "/");
        equivalentOperators.put(InfixExpression.Operator.REMAINDER, "%");

        equivalentOperators.put(InfixExpression.Operator.PLUS, "+");
        equivalentOperators.put(InfixExpression.Operator.MINUS, "-");

        // TODO: Test signed / unsigned semantics here
        equivalentOperators.put(InfixExpression.Operator.LEFT_SHIFT, "<<");
        equivalentOperators.put(InfixExpression.Operator.RIGHT_SHIFT_SIGNED, ">>");
        //cppOperators.put(InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED, "==");

        equivalentOperators.put(InfixExpression.Operator.LESS, "<");
        equivalentOperators.put(InfixExpression.Operator.GREATER, ">");
        equivalentOperators.put(InfixExpression.Operator.LESS_EQUALS, "<=");
        equivalentOperators.put(InfixExpression.Operator.GREATER_EQUALS, ">=");
        equivalentOperators.put(InfixExpression.Operator.EQUALS, "==");
        equivalentOperators.put(InfixExpression.Operator.NOT_EQUALS, "!=");

        equivalentOperators.put(InfixExpression.Operator.XOR, "^");
        equivalentOperators.put(InfixExpression.Operator.AND, "&");
        equivalentOperators.put(InfixExpression.Operator.OR, "|");

        equivalentOperators.put(InfixExpression.Operator.CONDITIONAL_AND, "&&");
        equivalentOperators.put(InfixExpression.Operator.CONDITIONAL_OR, "||");
    }

    @Override
    public void write(InfixExpression infixExpression) {
        InfixExpression.Operator operator = infixExpression.getOperator();

        if (operator == InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED) {
            writeRightShiftUnsigned(infixExpression);
        } else {
            writeNode(infixExpression.getLeftOperand());

            copySpaceAndComments();
            String operatorToken = this.equivalentOperators.get(operator);
            matchAndWrite(operatorToken);

            copySpaceAndComments();
            writeNode(infixExpression.getRightOperand());

            if (infixExpression.hasExtendedOperands()) {
                forEach(infixExpression.extendedOperands(), (Expression extendedOperand) -> {
                    copySpaceAndComments();
                    matchAndWrite(operatorToken);

                    copySpaceAndComments();
                    writeNode(extendedOperand);
                });
            }
        }
    }

    private void writeRightShiftUnsigned(InfixExpression infixExpression) {
        ITypeBinding typeBinding = infixExpression.getLeftOperand().resolveTypeBinding();
        String typeName = typeBinding.getName();

        //TODO: Remove inner parens for left operand if it's a simple (single elmt) expression, not needing them
        String cSharpTypeName;
        String cSharpUnsignedTypeName;
        if (typeBinding.getName().equals("long")) {
            cSharpTypeName = "long";
            cSharpUnsignedTypeName = "ulong";
        } else if (typeBinding.getName().equals("int")) {
            cSharpTypeName = "int";
            cSharpUnsignedTypeName = "uint";
        } else if (typeBinding.getName().equals("short")) {
            cSharpTypeName = "short";
            cSharpUnsignedTypeName = "ushort";
        } else if (typeBinding.getName().equals("byte")) {
            cSharpTypeName = "sbyte";
            cSharpUnsignedTypeName = "byte";
        }
        else throw new JUniversalException("Unexpected >>> left operand type: " + typeName);

        write("(" + cSharpTypeName + ")((" + cSharpUnsignedTypeName + ")(");
        writeNode(infixExpression.getLeftOperand());
        write(")");

        copySpaceAndComments();
        matchAndWrite(">>>", ">>");
        copySpaceAndComments();

        writeNode(infixExpression.getRightOperand());
        write(")");

        if (infixExpression.hasExtendedOperands())
            throw sourceNotSupported(">>> extended operands (with multiple >>> operators in a row, like 'a >>> b >>> c') not currently supported");
    }
}
