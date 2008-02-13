CLASSFILES=Main/Main.class Lex/BooleanLiteral.class			\
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
IR/State.class IR/SymbolTable.class IR/TagDescriptor.class		\
IR/TagVarDescriptor.class IR/TaskDescriptor.class			\
IR/TypeDescriptor.class IR/TypeUtil.class IR/VarDescriptor.class	\
IR/Virtual.class IR/Flat/BuildCode.class IR/Flat/BuildFlat.class	\
IR/Flat/FKind.class IR/Flat/FlatAtomicEnterNode.class			\
IR/Flat/FlatAtomicExitNode.class IR/Flat/FlatBackEdge.class		\
IR/Flat/FlatCall.class IR/Flat/FlatCastNode.class			\
IR/Flat/FlatCheckNode.class IR/Flat/FlatCondBranch.class		\
IR/Flat/FlatElementNode.class IR/Flat/FlatFieldNode.class		\
IR/Flat/FlatFlagActionNode.class IR/Flat/FlatGlobalConvNode.class	\
IR/Flat/FlatLiteralNode.class IR/Flat/FlatMethod.class			\
IR/Flat/FlatNew.class IR/Flat/FlatNode.class IR/Flat/FlatNop.class	\
IR/Flat/FlatOpNode.class IR/Flat/FlatReturnNode.class			\
IR/Flat/FlatSetElementNode.class IR/Flat/FlatSetFieldNode.class		\
IR/Flat/FlatTagDeclaration.class IR/Flat/NodePair.class			\
IR/Flat/FlatPrefetchNode.class						\
IR/Flat/ParamsObject.class IR/Flat/TempDescriptor.class			\
IR/Flat/TempFlagPair.class IR/Flat/TempObject.class			\
IR/Flat/TempTagPair.class IR/Tree/ArrayAccessNode.class			\
IR/Tree/AssignmentNode.class IR/Tree/AtomicNode.class			\
IR/Tree/BlockExpressionNode.class IR/Tree/BlockNode.class		\
IR/Tree/BlockStatementNode.class IR/Tree/BuildIR.class			\
IR/Tree/CastNode.class IR/Tree/ConstraintCheck.class			\
IR/Tree/CreateObjectNode.class IR/Tree/DNFFlag.class			\
IR/Tree/DNFFlagAtom.class IR/Tree/DeclarationNode.class			\
IR/Tree/ExpressionNode.class IR/Tree/FieldAccessNode.class		\
IR/Tree/FlagEffect.class IR/Tree/FlagEffects.class			\
IR/Tree/FlagExpressionNode.class IR/Tree/FlagNode.class			\
IR/Tree/FlagOpNode.class IR/Tree/IfStatementNode.class			\
IR/Tree/Kind.class IR/Tree/LiteralNode.class IR/Tree/LoopNode.class	\
IR/Tree/MethodInvokeNode.class IR/Tree/Modifiers.class			\
IR/Tree/NameNode.class IR/Tree/OpNode.class IR/Tree/ParseNode.class	\
IR/Tree/ParseNodeDOTVisitor.class IR/Tree/ParseNodeVector.class		\
IR/Tree/ReturnNode.class IR/Tree/SemanticCheck.class			\
IR/Tree/SubBlockNode.class IR/Tree/TagDeclarationNode.class		\
IR/Tree/TagEffect.class IR/Tree/TagExpressionList.class			\
IR/Tree/TaskExitNode.class IR/Tree/TreeNode.class			\
IR/Tree/Walkable.class Analysis/CallGraph/CallGraph.class		\
Analysis/Locality/GenerateConversions.class				\
Analysis/Locality/LocalityAnalysis.class				\
Analysis/Locality/LocalityBinding.class					\
Analysis/Locality/TempNodePair.class					\
Analysis/TaskStateAnalysis/Allocations.class				\
Analysis/TaskStateAnalysis/EGEdge.class					\
Analysis/TaskStateAnalysis/EGTaskNode.class				\
Analysis/TaskStateAnalysis/ExecutionGraph.class				\
Analysis/TaskStateAnalysis/FEdge.class					\
Analysis/TaskStateAnalysis/FlagState.class				\
Analysis/TaskStateAnalysis/GarbageAnalysis.class			\
Analysis/TaskStateAnalysis/OptionalTaskDescriptor.class			\
Analysis/TaskStateAnalysis/Predicate.class				\
Analysis/TaskStateAnalysis/SafetyAnalysis.class				\
Analysis/TaskStateAnalysis/TEdge.class					\
Analysis/TaskStateAnalysis/TagAnalysis.class				\
Analysis/TaskStateAnalysis/TagBinding.class				\
Analysis/TaskStateAnalysis/TaskAnalysis.class				\
Analysis/TaskStateAnalysis/TaskEdges.class				\
Analysis/TaskStateAnalysis/TaskGraph.class				\
Analysis/TaskStateAnalysis/TaskNode.class				\
Analysis/TaskStateAnalysis/FlagComparator.class				\
Analysis/TaskStateAnalysis/TaskNodeNamer.class Util/Edge.class		\
Analysis/FlatIRGraph/FlatIRGraph.class                                  \
Analysis/OwnershipAnalysis/OwnershipAnalysis.class                      \
Analysis/OwnershipAnalysis/OwnershipGraph.class                         \
Analysis/OwnershipAnalysis/OwnershipNode.class                          \
Analysis/OwnershipAnalysis/LabelNode.class                              \
Analysis/OwnershipAnalysis/HeapRegionNode.class                         \
Analysis/OwnershipAnalysis/ReferenceEdgeProperties.class                \
Analysis/OwnershipAnalysis/AllocationSite.class                         \
Util/GraphNode.class Util/Namer.class Util/Relation.class		\
Interface/HTTPHeader.class Interface/HTTPResponse.class			\
Interface/HTTPServices.class Interface/HashStrings.class		\
Interface/JhttpServer.class Interface/JhttpWorker.class			\
Interface/LogFile.class Interface/Pair.class				\
Interface/WebInterface.class Analysis/Prefetch/PrefetchAnalysis.class	\
Analysis/Prefetch/PrefetchPair.class Analysis/Prefetch/PairMap.class    \
Analysis/Prefetch/IndexDescriptor.class                                 \
Analysis/Scheduling/ClassNode.class                                     \
Analysis/Scheduling/ScheduleAnalysis.class                              \
Analysis/Scheduling/ScheduleEdge.class                                  \
Analysis/Scheduling/ScheduleNode.class



all: Parse/Sym.class Parse/Parser.class $(CLASSFILES) javadoc

wc:
	wc Interface/*.java Analysis/*/*.java IR/*.java IR/*/*.java Lex/*.java Util/*.java ClassLibrary/*.java

wcrun:
	wc Runtime/*.[c,h]

Parse/Parser.java Parse/Sym.java: Parse/java14.cup
	cd Parse && \
        java -cp ../../cup:$(CLASSPATH) java_cup.Main -parser Parser -symbols Sym < java14.cup

%.class: %.java
	javac -cp ../cup:.:$(CLASSPATH) $<

javadoc:
	mkdir javadoc
	javadoc -classpath ../cup:.:$(CLASSPATH) -sourcepath . -private -d javadoc Lex Util IR IR.Tree IR.Flat Analysis Analysis.CallGraph Analysis.Flag Analysis.TaskStateAnalysis Analysis.Locality Analysis.Prefetch Main Analysis.OwnershipAnalysis

clean:
	rm -f IR/*.class IR/Tree/*.class Main/*.class Lex/*.class Parse/*.class Parse/Sym.java Parse/Parser.java IR/Flat/*.class classdefs.h methodheaders.h methods.c structdefs.h virtualtable.h task.h taskdefs.c taskdefs.h Analysis/*.class Analysis/Flag/*.class Analysis/CallGraph/*.class  Analysis/TaskStateAnalysis/*.class Interface/*.class Util/*.class Analysis/Locality/*.class Analysis/Prefetch/*.class Analysis/FlatIRGraph/*.class Analysis/OwnershipAnalysis/*.class Analysis/Scheduling/*.class

cleandoc:
	rm -rf javadoc
