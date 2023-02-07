package lanat;

import lanat.utils.UtlString;
import lanat.utils.displayFormatter.TextFormatter;

public record Token(TokenType type, String contents) {
	public TextFormatter getFormatter() {
		var contents = this.contents();
		if (contents.contains(" ") && this.type == TokenType.ARGUMENT_VALUE) {
			contents = UtlString.surround(contents, "'");
		}
		return new TextFormatter(contents, this.type.color);
	}
}