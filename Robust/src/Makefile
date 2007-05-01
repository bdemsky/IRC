CLASSFILES= Main/Main.class Lex/BooleanLiteral.class			\
Lex/CharacterLiteral.class Lex/Comment.class				\
Lex/DocumentationComment.class Lex/DoubleLiteral.class Lex/EOF.class	\
Lex/EndOfLineComment.class Lex/EscapedUnicodeReader.class		\
Lex/FIFO.class Lex/FloatLiteral.class Lex/Identifier.class		\
Lex/InputElement.class Lex/IntegerLiteral.class Lex/Keyword.class	\
Lex/Lexer.class Lex/Literal.class Lex/LongLiteral.class			\
Lex/NullLiteral.class Lex/NumericLiteral.class Lex/Operator.class	\
Lex/Separator.class Lex/StringLiteral.class Lex/Token.class		\
Lex/TraditionalComment.class Lex/WhiteSpace.class			\
IR/AssignOperation.class IR/ClassDescriptor.class IR/Descriptor.class	\
IR/FieldDescriptor.class IR/FlagDescriptor.class			\
IR/MethodDescriptor.class IR/NameDescriptor.class IR/Operation.class	\
IR/State.class IR/SymbolTable.class IR/TaskDescriptor.class		\
IR/TypeDescriptor.class IR/TypeUtil.class IR/VarDescriptor.class	\
IR/Virtual.class IR/Tree/ArrayAccessNode.class				\
IR/Tree/AssignmentNode.class IR/Tree/BlockExpressionNode.class		\
IR/Tree/BlockNode.class IR/Tree/BlockStatementNode.class		\
IR/Tree/BuildIR.class IR/Tree/CastNode.class				\
IR/Tree/ConstraintCheck.class IR/Tree/CreateObjectNode.class		\
IR/Tree/DNFFlag.class IR/Tree/DNFFlagAtom.class				\
IR/Tree/DeclarationNode.class IR/Tree/ExpressionNode.class		\
IR/Tree/FieldAccessNode.class IR/Tree/FlagEffect.class			\
IR/Tree/FlagEffects.class IR/Tree/FlagExpressionNode.class		\
IR/Tree/FlagNode.class IR/Tree/FlagOpNode.class				\
IR/Tree/IfStatementNode.class IR/Tree/Kind.class			\
IR/Tree/LiteralNode.class IR/Tree/LoopNode.class			\
IR/Tree/MethodInvokeNode.class IR/Tree/Modifiers.class			\
IR/Tree/NameNode.class IR/Tree/OpNode.class IR/Tree/ParseNode.class	\
IR/Tree/ParseNodeDOTVisitor.class IR/Tree/ParseNodeVector.class		\
IR/Tree/ReturnNode.class IR/Tree/SemanticCheck.class			\
IR/Tree/SubBlockNode.class IR/Tree/TaskExitNode.class			\
IR/Tree/TreeNode.class IR/Tree/Walkable.class IR/Flat/BuildCode.class	\
IR/Flat/BuildFlat.class IR/Flat/FKind.class IR/Flat/FlatCall.class	\
IR/Flat/FlatCastNode.class IR/Flat/FlatCheckNode.class			\
IR/Flat/FlatCondBranch.class IR/Flat/FlatElementNode.class		\
IR/Flat/FlatFieldNode.class IR/Flat/FlatFlagActionNode.class		\
IR/Flat/FlatLiteralNode.class IR/Flat/FlatMethod.class			\
IR/Flat/FlatNew.class IR/Flat/FlatNode.class IR/Flat/FlatNop.class	\
IR/Flat/FlatOpNode.class IR/Flat/FlatReturnNode.class			\
IR/Flat/FlatSetElementNode.class IR/Flat/FlatSetFieldNode.class		\
IR/Flat/NodePair.class IR/Flat/ParamsObject.class			\
IR/Flat/TempDescriptor.class IR/Flat/TempFlagPair.class			\
IR/Flat/TempObject.class Util/Relation.class

all: Parse/Sym.class Parse/Parser.class $(CLASSFILES) javadoc

Parse/Parser.java Parse/Sym.java: Parse/java14.cup
	cd Parse && \
        java -cp ../../cup:$(CLASSPATH) java_cup.Main -parser Parser -symbols Sym < java14.cup

%.class: %.java
	javac -cp ../cup:.:$(CLASSPATH) $<

javadoc:
	mkdir javadoc
	javadoc -classpath ../cup:.:$(CLASSPATH) -sourcepath . -private -d javadoc Lex Util IR IR.Tree IR.Flat Analysis Analysis.CallGraph Analysis.Flag Analysis.TaskStateAnalysis Main 

clean:
	rm -r javadoc
	rm IR/*.class IR/Tree/*.class Main/*.class Lex/*.class Parse/*.class Parse/Sym.java Parse/Parser.java IR/Flat/*.class classdefs.h methodheaders.h methods.c structdefs.h virtualtable.h task.h taskdefs.c taskdefs.h Analysis/*.class Analysis/Flag/*.class Analysis/CallGraph/*.class  Analysis/TaskStateAnalysis/*.class

