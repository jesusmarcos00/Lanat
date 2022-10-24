package argparser;

import java.util.ArrayList;

public class ArgumentParser {
	protected final String programName, description;
	protected ArrayList<Argument<?, ?>> arguments = new ArrayList<>();
	private TupleCharacter tupleCharacter = TupleCharacter.SquareBrackets;


	public ArgumentParser(String programName, String description) {
		this.programName = programName;
		this.description = description;
		this.addArgument(new Argument<>("help", ArgumentType.BOOLEAN())
			.callback(t -> System.out.println(this.getHelp()))
		);
	}

	public ArgumentParser(String programName) {
		this(programName, "");
	}

	public String getHelp() {
		return "This is the help of the program.";
	}

	public <T extends ArgumentType<TInner>, TInner>
	void addArgument(Argument<T, TInner> argument) {
		if (this.arguments.stream().anyMatch(a -> a.equals(argument))) {
			throw new IllegalArgumentException("duplicate argument identifiers");
		}
		arguments.add(argument);
	}

	public ParsedArguments parseArgs(String[] args) throws Exception {
		// if we receive the classic args array, just join it back
		return this.parseArgs(String.join(" ", args));
	}

	public ParsedArguments parseArgs(String args) throws Exception {
		ParserState ps = new ParserState(args, this.arguments, tupleCharacter);
		return new ParsedArguments(ps.parse());
	}

	public ArgumentParser tupleCharacter(TupleCharacter tupleCharacter) {
		this.tupleCharacter = tupleCharacter;
		return this;
	}
}