package com.gr.gateway.config

import cats.effect.IO
import munit.CatsEffectSuite

class ConfigSpec extends CatsEffectSuite:

  test("config is resolvable") {
    Config.load[IO]()
  }
