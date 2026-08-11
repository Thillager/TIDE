import com.formdev.flatlaf.FlatDarkLaf;

public class ThillagersFirstTheme
	extends FlatDarkLaf
{
	public static final String NAME = "ThillagersFirstTheme";

	public static boolean setup() {
		return setup( new ThillagersFirstTheme() );
	}

	public static void installLafInfo() {
		installLafInfo( NAME, ThillagersFirstTheme.class );
	}

	@Override
	public String getName() {
		return NAME;
	}
}
