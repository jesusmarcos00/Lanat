package lanat.parsing;

import lanat.Command;
import lanat.parsing.errors.TokenizeError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Tokenizer extends ParsingStateBase<TokenizeError> {
	/** Are we currently within a tuple? */
	protected boolean tupleOpen = false;

	/** Are we currently within a string? */
	protected boolean stringOpen = false;

	/** The index of the current character in the {@link Tokenizer#inputString} */
	private int currentCharIndex = 0;

	/** The tokens that have been parsed so far */
	private final @NotNull List<@NotNull Token> finalTokens = new ArrayList<>();

	/** The current value of the token that is being parsed */
	private final @NotNull StringBuilder currentValue = new StringBuilder();

	/** The input string that is being tokenized */
	private String inputString;

	/** The input string that is being tokenized, split into characters */
	private char[] inputChars;

	final char tupleOpenChar = this.command.getTupleChars().open;
	final char tupleCloseChar = this.command.getTupleChars().close;


	public Tokenizer(@NotNull Command command) {
		super(command);
	}

	// ------------------------------------------------ Error Handling ------------------------------------------------
	void addError(@NotNull TokenizeError.TokenizeErrorType type, int index) {
		this.addError(new TokenizeError(type, index));
	}
	// ------------------------------------------------ ////////////// ------------------------------------------------

	private void setInputString(@NotNull String inputString) {
		this.inputString = inputString;
		this.inputChars = inputString.toCharArray();
	}

	/**
	 * Tokenizes the input string given. When finished, the tokens can be retrieved using
	 * {@link Tokenizer#getFinalTokens()}
	 */
	public void tokenize(@NotNull String input) {
		assert !this.hasFinished : "Tokenizer has already finished tokenizing";

		this.setInputString(input);

		char currentStringChar = 0; // the character that opened the string
		TokenizeError.TokenizeErrorType errorType = null;

		for (
			this.currentCharIndex = 0;
			this.currentCharIndex < this.inputChars.length && !this.hasFinished;
			this.currentCharIndex++
		) {
			char cChar = this.inputChars[this.currentCharIndex];

			// user is trying to escape a character
			if (cChar == '\\') {
				this.currentValue.append(this.inputChars[++this.currentCharIndex]); // skip the \ character and append the next character

				// reached a possible value wrapped in quotes
			} else if (cChar == '"' || cChar == '\'') {
				// if we are already in an open string, push the current value and close the string. Make sure
				// that the current char is the same as the one that opened the string
				if (this.stringOpen && currentStringChar == cChar) {
					this.addToken(TokenType.ARGUMENT_VALUE, this.currentValue.toString());
					this.currentValue.setLength(0);
					this.stringOpen = false;

					// the string is open, but the character does not match. Push it as a normal character
				} else if (this.stringOpen) {
					this.currentValue.append(cChar);

					// the string is not open, so open it and set the current string char to the current char
				} else {
					this.stringOpen = true;
					currentStringChar = cChar;
				}

				// append characters to the current value as long as we are in a string
			} else if (this.stringOpen) {
				this.currentValue.append(cChar);

				// reached a possible tuple start character
			} else if (cChar == this.tupleOpenChar) {
				// if we are already in a tuple, set error and stop tokenizing
				if (this.tupleOpen) {
					errorType = TokenizeError.TokenizeErrorType.TUPLE_ALREADY_OPEN;
					break;
				} else if (!this.currentValue.isEmpty()) { // if there was something before the tuple, tokenize it
					this.tokenizeCurrentValue();
				}

				// push the tuple token and set the state to tuple open
				this.addToken(TokenType.ARGUMENT_VALUE_TUPLE_START, this.tupleOpenChar);
				this.tupleOpen = true;

				// reached a possible tuple end character
			} else if (cChar == this.tupleCloseChar) {
				// if we are not in a tuple, set error and stop tokenizing
				if (!this.tupleOpen) {
					errorType = TokenizeError.TokenizeErrorType.UNEXPECTED_TUPLE_CLOSE;
					break;
				}

				// if there was something before the tuple, tokenize it
				if (!this.currentValue.isEmpty()) {
					this.addToken(TokenType.ARGUMENT_VALUE, this.currentValue.toString());
				}

				// push the tuple token and set the state to tuple closed
				this.addToken(TokenType.ARGUMENT_VALUE_TUPLE_END, this.tupleCloseChar);
				this.currentValue.setLength(0);
				this.tupleOpen = false;

				// reached a "--". Push all the rest as a FORWARD_VALUE.
			} else if (
				cChar == '-'
					&& this.isCharAtRelativeIndex(1, '-')
					&& this.isCharAtRelativeIndex(2, ' ')
			)
			{
				this.addToken(TokenType.FORWARD_VALUE, this.inputString.substring(this.currentCharIndex + 3));
				break;

				// reached a possible separator
			} else if (
				(cChar == ' ' && !this.currentValue.isEmpty()) // there's a space and some value to tokenize
					// also check if this is defining the value of an argument, or we are in a tuple. If so, don't tokenize
					|| (cChar == '=' && !this.tupleOpen && this.isArgumentSpecifier(this.currentValue.toString()))
			)
			{
				this.tokenizeCurrentValue();

				// push the current char to the current value
			} else if (cChar != ' ') {
				this.currentValue.append(cChar);
			}
		}

		if (errorType == null)
			if (this.tupleOpen) {
				errorType = TokenizeError.TokenizeErrorType.TUPLE_NOT_CLOSED;
			} else if (this.stringOpen) {
				errorType = TokenizeError.TokenizeErrorType.STRING_NOT_CLOSED;
			}

		// we left something in the current value, tokenize it
		if (!this.currentValue.isEmpty()) {
			this.tokenizeCurrentValue();
		}

		if (errorType != null) {
			this.addError(errorType, this.finalTokens.size());
		}

		this.hasFinished = true;
	}

	/** Inserts a token into the final tokens list with the given type and contents */
	private void addToken(@NotNull TokenType type, @NotNull String contents) {
		this.finalTokens.add(new Token(type, contents));
	}

	/** Inserts a token into the final tokens list with the given type and contents */
	private void addToken(@NotNull TokenType type, char contents) {
		this.finalTokens.add(new Token(type, String.valueOf(contents)));
	}

	/**
	 * Tokenizes a single word and returns the token matching it. If no match could be found, returns
	 * {@link TokenType#ARGUMENT_VALUE}
	 */
	private @NotNull Token tokenizeWord(@NotNull String str) {
		final TokenType type;

		if (this.tupleOpen || this.stringOpen) {
			type = TokenType.ARGUMENT_VALUE;
		} else if (this.isArgName(str)) {
			type = TokenType.ARGUMENT_NAME;
		} else if (this.isArgNameList(str)) {
			type = TokenType.ARGUMENT_NAME_LIST;
		} else if (this.isSubCommand(str)) {
			type = TokenType.COMMAND;
		} else {
			type = TokenType.ARGUMENT_VALUE;
		}

		return new Token(type, str);
	}

	/**
	 * Tokenizes the {@link Tokenizer#currentValue} and adds it to the final tokens list.
	 * <p>
	 * If the token is a Sub-Command, it will forward the rest of the input string to the Sub-Command's tokenizer.
	 * </p>
	 */
	private void tokenizeCurrentValue() {
		final Token token = this.tokenizeWord(this.currentValue.toString());
		Command subCmd;
		// if this is a Sub-Command, continue tokenizing next elements
		if (token.type() == TokenType.COMMAND && (subCmd = this.getSubCommandByName(token.contents())) != null) {
			// forward the rest of stuff to the Sub-Command
			subCmd.getTokenizer().tokenize(this.inputString.substring(this.currentCharIndex));
			this.hasFinished = true;
		} else {
			this.finalTokens.add(token);
		}
		this.currentValue.setLength(0);
	}

	/**
	 * Returns <code>true</code> if the given string can be an argument name list, eg: <code>"-fbq"</code>.
	 * <p>
	 * This returns <code>true</code> if at least the first character is a valid argument prefix and at least one of the
	 * next characters is a valid argument name.
	 * <br><br>
	 * For a prefix to be valid, it must be a character used as a prefix on the next argument/s specified.
	 * </p>
	 */
	private boolean isArgNameList(@NotNull String str) {
		if (str.length() < 2) return false;

		final var possiblePrefixes = new ArrayList<Character>();
		final var charArray = str.substring(1).toCharArray();

		for (final char argName : charArray) {
			if (!this.runForArgument(argName, a -> possiblePrefixes.add(a.getPrefix().character)))
				break;
		}

		return possiblePrefixes.size() >= 1 && possiblePrefixes.contains(str.charAt(0));
	}

	/**
	 * Returns <code>true</code> if the given string can be an argument name, eg: <code>"--help"</code>.
	 * <p>
	 * This returns <code>true</code> if the given string is a valid argument name with a double prefix.
	 * </p>
	 */
	private boolean isArgName(@NotNull String str) {
		// make sure we are working with long enough strings
		return str.length() > 1 && this.getArguments().stream().anyMatch(a -> a.checkMatch(str));
	}

	/**
	 * Returns <code>true</code> whether the given string is an argument name {@link Tokenizer#isArgName(String)} or an
	 * argument name list {@link Tokenizer#isArgNameList(String)}.
	 */
	private boolean isArgumentSpecifier(@NotNull String str) {
		return this.isArgName(str) || this.isArgNameList(str);
	}

	/** Returns <code>true</code> if the given string is a Sub-Command name */
	private boolean isSubCommand(@NotNull String str) {
		return this.getCommands().stream().anyMatch(c -> c.hasName(str));
	}

	/**
	 * Returns <code>true</code> if the character of {@link Tokenizer#inputChars} at a relative index from
	 * {@link Tokenizer#currentCharIndex} is equal to the specified character.
	 * <p>
	 * If the index is out of bounds, returns <code>false</code>.
	 * </p>
	 */
	private boolean isCharAtRelativeIndex(int index, char character) {
		index += this.currentCharIndex;
		if (index >= this.inputChars.length || index < 0) return false;
		return this.inputChars[index] == character;
	}

	/** Returns a command from the Sub-Commands of {@link Tokenizer#command} that matches the given name */
	private Command getSubCommandByName(@NotNull String name) {
		var x = this.getCommands().stream().filter(sc -> sc.hasName(name)).toList();
		return x.isEmpty() ? null : x.get(0);
	}

	/**
	 * Returns a list of all tokenized Sub-Command children of {@link Tokenizer#command}.
	 * <p>
	 * Note that a Command only has a single tokenized Sub-Command, so this will have one Command per nesting level.
	 * </p>
	 */
	public @NotNull List<@NotNull Command> getTokenizedCommands() {
		final List<Command> x = new ArrayList<>();
		final Command subCmd;

		x.add(this.command);
		if ((subCmd = this.getTokenizedSubCommand()) != null) {
			x.addAll(subCmd.getTokenizer().getTokenizedCommands());
		}
		return x;
	}

	/** Returns the tokenized Sub-Command of {@link Tokenizer#command}. */
	public @Nullable Command getTokenizedSubCommand() {
		return this.getCommands().stream()
			.filter(sb -> sb.getTokenizer().hasFinished)
			.findFirst()
			.orElse(null);
	}

	/** Returns the list of all tokens that have been tokenized. */
	public @NotNull List<@NotNull Token> getFinalTokens() {
		assert this.hasFinished : "Cannot get final tokens before tokenizing has finished";
		return this.finalTokens;
	}

	public boolean isFinishedTokenizing() {
		return this.hasFinished;
	}
}
