# InputSim

A SuperCollider Quark for simulating incoming OSC data.

Quickly set up a simple GUI for generating incoming data in the form of OSC messages. This is useful for simulating data from physical sensors, external controllers, etc.

## Installation

To install this quark, simply run this line from within SuperCollider:

```
Quarks.install("https://github.com/sparkletop/InputSim")
```

In order to use any newly downloaded Quark, restart SuperCollider or recompile the class library.

This assumes [git](https://git-scm.com/) is installed on your system. See the [SuperCollider docs](http://doc.sccode.org/Guides/UsingQuarks.html) for more info.

## Get started

Run this line from within SuperCollider to start a simple InputSim GUI:

```
InputSim()
```

Further documentation is integrated into SuperCollider's help system. To access this documentation, run `InputSim.help`.
