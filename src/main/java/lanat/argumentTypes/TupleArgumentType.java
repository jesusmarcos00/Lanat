package lanat.argumentTypes;

import lanat.ArgValueCount;
import lanat.ArgumentType;
import lanat.utils.displayFormatter.Color;
import lanat.utils.displayFormatter.TextFormatter;


public abstract class TupleArgumentType<T> extends ArgumentType<T> {
	private final ArgValueCount argCount;

	public TupleArgumentType(ArgValueCount argValueCount, T initialValue) {
		this.argCount = argValueCount;
		this.setValue(initialValue);
	}

	@Override
	public ArgValueCount getNumberOfArgValues() {
		return this.argCount;
	}

	@Override
	public TextFormatter getRepresentation() {
		return new TextFormatter(this.getValue().getClass().getSimpleName())
			.concat(new TextFormatter(this.argCount.getRegexRange()).setColor(Color.BRIGHT_YELLOW));
	}
}