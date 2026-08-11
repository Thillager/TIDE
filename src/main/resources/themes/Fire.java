import com.formdev.flatlaf.FlatDarkLaf;

public class Fire
	extends FlatDarkLaf
{
	public static final String NAME = "ThillagersFirstTheme";

	public static boolean setup() {
		return setup( new Fire() );
	}

	public static void installLafInfo() {
		installLafInfo( NAME, Fire.class );
	}

	@Override
	public String getName() {
		return NAME;
	}
}
