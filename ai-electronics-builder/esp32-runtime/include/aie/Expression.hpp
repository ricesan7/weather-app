#pragma once

#include "aie/Value.hpp"
#include <string>
#include <unordered_map>

namespace aie {

class ExpressionEvaluator {
public:
    bool evaluate(
        const std::string& expression,
        const std::unordered_map<std::string, Value>& context
    ) const;
};

} // namespace aie
