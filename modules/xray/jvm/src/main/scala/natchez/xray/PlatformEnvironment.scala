// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.xray

private[xray] trait PlatformEnvironment {
  def env = sys.env
}
