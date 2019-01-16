@GrabResolver(name='gate-snapshots', root='http://repo.gate.ac.uk/content/groups/public/')
@Grab('uk.ac.gate:gate-core:8.6-SNAPSHOT')
import gate.*;

// initialize GATE
Gate.init();

// Load the plugin the class we want to use is in
Gate.getCreoleRegister().registerPlugin(new Plugin.Maven("uk.ac.gate.plugins","stringannotation","4.2-SNAPSHOT"));

// Get an instance of the class we want access to.
def cacheGenerator = Gate.getClassLoader().loadClass("com.jpetrak.gate.stringannotation.extendedgazetteer.GenerateCache");

// call the main method passing this scripts args straight through
cacheGenerator.main(args);
