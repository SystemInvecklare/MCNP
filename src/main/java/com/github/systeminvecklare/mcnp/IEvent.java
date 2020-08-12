package com.github.systeminvecklare.mcnp;

/*package-protected*/ interface IEvent<L> {
	void fireFor(L listener);
}
