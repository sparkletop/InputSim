InputSim {
	var <paths, <min, <max,
	<waitMin, <waitMax, <targetAddr,
	sSpec, wSpec, <waitTime, modeMenu,
	emitters, emittersPanel, onOffBtn,
	wNumBox, wSlider, <window;

	*new {
		arg paths = ['/default'],
		min = 0, max = 127,
		waitMin = 0.01, waitMax = 1;
		^super.newCopyArgs(paths, min, max, waitMin, waitMax).init;
	}

	free {
		window.close;
	}

	targetAddr_ { | newTargetAddr |
		if (newTargetAddr.isKindOf(NetAddr),
			{ targetAddr = newTargetAddr },
			{ "targetAddr must be a NetAddr".error }
		);
	}

	play { onOffBtn.valueAction_(1) }

	stop { onOffBtn.valueAction_(0)	}

	paths_ { | pathsArr |	paths = pathsArr;	this.init; }

	min_ { | num |	min = num;	this.init; }

	max_ { | num |	max = num;	this.init; }

	waitMin_ { | num |
		if (num > 0,
			{ waitMin = num },
			{"Minimum wait time must be a positive number".error}
		);
		this.init;
	}

	waitMax_ { | num |
		if(num > waitMin,
			{ waitMax = num },
			{"Maximum wait time must be greater than minimum wait time".error}
		);
		this.init;
	}

	waitTime_ { | num |
		waitTime = num.clip(waitMin, waitMax);
		wNumBox.valueAction_(waitTime);
	}

	init {
		window !? {
			emitters.do({|s|s.stop});
			window.close;
		};

		if (paths.isKindOf(SequenceableCollection).not, {
			paths = [paths.asSymbol];
		});

		sSpec = ControlSpec(min, max, \lin, 1);
		wSpec = ControlSpec(waitMin, waitMax, 1.5);
		targetAddr = targetAddr ?? NetAddr.localAddr;
		waitTime = waitTime ?? [waitMax, waitMin].mean.round(0.1);

		emitters = Dictionary.new;
		emittersPanel = HLayout();

		// on/off button
		onOffBtn = Button().states_([
			["Off"],
			["On", Color.black, Color(0.5,1,0.5)]
		]).action_({ | button |
			if (button.value == 1,
				{emitters.do({|s|s.reset.play})},
				{emitters.do({|s|s.stop})},
			);
		});

		// wait time control panel
		wNumBox = NumberBox()
		.clipLo_(waitMin).clipHi_(waitMax)
		.maxDecimals_(2)
		.step_((waitMax-waitMin)*0.01)
		.scroll_step_((waitMax-waitMin)*0.01)
		.align_(\center)
		.maxWidth_(60)
		.action_({ |box|
			wSlider.value = wSpec.unmap(box.value);
			waitTime = box.value;
		});

		wSlider = Knob()
		.action_({
			wNumBox.value = wSpec.map(wSlider.value);
			waitTime = wNumBox.value;
		});

		wNumBox.valueAction_(waitTime);

		// emitters
		paths.do{
			arg path;
			var streams,
			modes = ["Static", "White noise", "Brown noise", "Lo rand", "Hi rand"],

			rMin = min,
			rMax = max,

			mode = modes.first,

			initStreams = {
				streams = Dictionary.newFrom([
					modes,
					[
						min,
						Pwhite(rMin, rMax).asStream,
						Pbrown(rMin, rMax, (rMax-rMin) * 0.1).asStream,
						Pwhite().pow(4)
						.linlin(0,1,rMin,rMax-((rMax - rMin) * 0.25)).asStream,
						Pwhite().pow(4)
						.linlin(0,1,rMax,rMin+((rMax - rMin) * 0.25)).asStream
					]
				].lace);
			},

			modeMenu = PopUpMenu()
			.items_(modes)
			.value_(0)
			.action_({ | menu |
				rSlider.enabled_(menu.value > 0);
				mode = modes[menu.value];
			}),

			slider = Slider()
			.action_({ | in |
				numBox.value = sSpec.map(in.value);
				streams[modes.first] = numBox.value;
			}),

			rSlider = RangeSlider()
			.enabled_(false)
			.action_({ | slider |
				rMin = sSpec.map(slider.lo);
				rMax = sSpec.map(slider.hi);
				initStreams.value;
			}),

			numBox = NumberBox()
			.action_({
				slider.value = sSpec.unmap(numBox.value);
				streams[modes.first] = numBox.value;
			})
			.align_(\center)
			.maxDecimals_(0)
			.clipLo_(0.01).clipHi_(max);

			initStreams.value;

			emitters[path] = Routine({
				{
					{
						numBox.valueAction_(streams[mode].next);
						targetAddr.sendMsg(path, streams[modes.first]);
					}.defer;
					waitTime.wait;
				}.loop
			});

			emittersPanel.add(
				VLayout(
					StaticText().string_(path),
					HLayout(slider, rSlider),
					numBox,
					modeMenu
			));
		};
		paths.postln;

		// put the elements together
		window = Window.new(
			"InputSim",
			Rect(
				rrand(1000,1500),
				rrand(300,600),
				paths.size*150,
				500)
		)
		.alwaysOnTop_(true)
		.layout_(VLayout(
			HLayout(
				onOffBtn,
				StaticText()
				.string_("Wait")
				.align_(\right),
				wSlider,
				wNumBox
			)
			.setStretch(1,10)
			.setStretch(0,4),
			emittersPanel
		))
		.onClose_({emitters.do{|s|s.stop}})
		.front;
	}
}
