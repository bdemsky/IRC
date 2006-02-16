CLASSFILES=Main/Main.class IR/AssignOperation.class IR/Descriptor.class	\
IR/FieldDescriptor.class IR/MethodDescriptor.class			\
IR/NameDescriptor.class IR/Operation.class IR/State.class		\
IR/SymbolTable.class IR/Tree/AssignmentNode.class			\
IR/Tree/BlockExpressionNode.class IR/Tree/BlockNode.class		\
IR/Tree/BlockStatementNode.class IR/Tree/BuildIR.class			\
IR/Tree/CastNode.class IR/Tree/CreateObjectNode.class			\
IR/Tree/DeclarationNode.class IR/Tree/ExpressionNode.class		\
IR/Tree/FieldAccessNode.class IR/Tree/IfStatementNode.class		\
IR/Tree/LiteralNode.class IR/Tree/LoopNode.class			\
IR/Tree/MethodInvokeNode.class IR/Tree/Modifiers.class			\
IR/Tree/NameNode.class IR/Tree/OpNode.class IR/Tree/ParseNode.class	\
IR/Tree/ParseNodeDOTVisitor.class IR/Tree/ParseNodeVector.class		\
IR/Tree/ReturnNode.class IR/Tree/SubBlockNode.class			\
IR/Tree/TreeNode.class IR/Tree/Walkable.class IR/TypeDescriptor.class	\
IR/VarDescriptor.class Lex/BooleanLiteral.class				\
Lex/CharacterLiteral.class Lex/Comment.class				\
Lex/DocumentationComment.class Lex/DoubleLiteral.class			\
Lex/EndOfLineComment.class Lex/EOF.class				\
Lex/EscapedUnicodeReader.class Lex/FIFO.class Lex/FloatLiteral.class	\
Lex/Identifier.class Lex/InputElement.class Lex/IntegerLiteral.class	\
Lex/Keyword.class Lex/Lexer.class Lex/Literal.class			\
Lex/LongLiteral.class Lex/NullLiteral.class Lex/NumericLiteral.class	\
Lex/Operator.class Lex/Separator.class Lex/StringLiteral.class		\
Lex/Token.class Lex/TraditionalComment.class Lex/WhiteSpace.class

all: Parse/Sym.class Parse/Parser.class $(CLASSFILES)

Parse/Parser.java Parse/Sym.java: Parse/java14.cup
	cd Parse && \
        java -cp ../../cup:$(CLASSPATH) java_cup.Main -parser Parser -symbols Sym < java14.cup

%.class: %.java
	javac -cp ../cup:.:$(CLASSPATH) $<

clean:
	rm IR/*.class IR/Tree/*.class Main/*.class Lex/*.class Parse/*.class Parse/Sym.java Parse/Parser.java IR/Flat/*.class
