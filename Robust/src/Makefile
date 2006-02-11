CLASSFILES=Main/Main.class Lex/BooleanLiteral.class			\
Lex/CharacterLiteral.class Lex/Comment.class				\
Lex/DocumentationComment.class Lex/EndOfLineComment.class		\
Lex/EOF.class Lex/EscapedUnicodeReader.class Lex/FIFO.class		\
Lex/FloatLiteral.class Lex/Identifier.class Lex/InputElement.class	\
Lex/IntegerLiteral.class Lex/Keyword.class Lex/Lexer.class		\
Lex/Literal.class Lex/LongLiteral.class Lex/NullLiteral.class		\
Lex/NumericLiteral.class Lex/Operator.class Lex/Separator.class		\
Lex/StringLiteral.class Lex/Token.class Lex/TraditionalComment.class	\
Lex/WhiteSpace.class IR/Tree/ParseNode.class				\
IR/Tree/ParseNodeDOTVisitor.class IR/Tree/ParseNodeVector.class		\
IR/Tree/Walkable.class IR/State.class IR/SymbolTable.class		\
IR/Descriptor.class IR/Tree/Modifiers.class IR/Tree/FileNode.class	\
IR/Tree/ClassNode.class IR/Tree/TreeNode.class IR/Tree/BuildIR.class	\
IR/TypeDescriptor.class IR/FieldDescriptor.class IR/Operation.class	\
IR/Tree/OpNode.class IR/Tree/LiteralNode.class				\
IR/Tree/ExpressionNode.class

all: Parse/Sym.class Parse/Parser.class $(CLASSFILES)

Parse/Parser.java Parse/Sym.java: Parse/java14.cup
	cd Parse && \
        java -cp ../../cup:$(CLASSPATH) java_cup.Main -parser Parser -symbols Sym < java14.cup

%.class: %.java
	javac -cp ../cup:.:$(CLASSPATH) $<

clean:
	rm IR/*.class IR/Tree/*.class Main/*.class Lex/*.class Parse/*.class Parse/Sym.java Parse/Parser.java
