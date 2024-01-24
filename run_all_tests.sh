#!/usr/bin/env bash

sbt clean compile scalafmtAll scalastyleAll coverage test dependencyUpdates coverageOff coverageReport
