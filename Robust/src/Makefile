CLASSFILES=Main/Main.class Lex/BooleanLiteral.class			\
Lex/CharacterLiteral.class Lex/Comment.class				\
Lex/DocumentationComment.class Lex/EndOfLineComment.class		\
Lex/EOF.class Lex/EscapedUnicodeReader.class Lex/FIFO.class		\
Lex/FloatLiteral.class Lex/Identifier.class Lex/InputElement.class	\
Lex/IntegerLiteral.class Lex/Keyword.class Lex/Lexer.class		\
Lex/Literal.class Lex/LongLiteral.class Lex/NullLiteral.class		\
Lex/NumericLiteral.class Lex/Operator.class Lex/Separator.class		\
Lex/StringLiteral.class Lex/Token.class Lex/TraditionalComment.class	\
Lex/WhiteSpace.class Tree/ParseNode.class				\
Tree/ParseNodeDOTVisitor.class Tree/ParseNodeVector.class		\
Tree/Walkable.class

all: Parse/Sym.class Parse/Parser.class $(CLASSFILES)

Parse/Parser.java Parse/Sym.java: Parse/java14.cup
	cd Parse && \
        java -cp ../../cup:$(CLASSPATH) java_cup.Main -parser Parser -symbols Sym < java14.cup

%.class: %.java
	javac -cp ../cup:.:$(CLASSPATH) $<

clean:
	rm Tree/*.class Main/*.class Lex/*.class Parse/*.class Parse/Sym.java Parse/Parser.java
